package com.betgol.receipt.infrastructure.clients.bonus

import com.betgol.receipt.config.BonusClientConfig
import com.betgol.receipt.domain.Ids.{BonusApiSessionCode, BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.{BonusApiClient, BonusApiError, BonusApiSession, BonusAssignmentResultStatus}
import com.betgol.receipt.domain.repos.BonusApiSessionRepository
import zio.*
import zio.http.*
import zio.test.*


object BonusXmlApiClientSpec extends ZIOSpecDefault {

  class InMemorySessionRepo(data: Ref[Option[BonusApiSession]], val sessionReadCounter: Ref[Int]) extends BonusApiSessionRepository {
    override def getSession: IO[Nothing, Option[BonusApiSession]] = sessionReadCounter.update(_ + 1) *> data.get
    override def saveSession(session: BonusApiSession): IO[Nothing, Unit] = data.set(Some(session))
    def clear: UIO[Unit] = data.set(None)
  }

  case class MockClientBehavior(failOnLogin: Ref[Boolean])
  object MockClientBehavior {
    val layer: ZLayer[Any, Nothing, MockClientBehavior] =
      ZLayer.fromZIO(Ref.make(false).map(MockClientBehavior(_)))
  }

  val xmlLoginSuccess = """<mtl><head><sessioncode>FRESH_SESSION</sessioncode></head></mtl>"""
  val xmlLoginFailed =
    """<mtl version="5.3">
      |    <head serverversion="1.0.75">
      |        <status>401</status>
      |        <sessioncode>00000000-0000-0000-0000-000000000000</sessioncode>
      |    </head>
      |    <body>Session error!!!</body>
      |</mtl>""".stripMargin
  val xmlSessionExpired = """<mtl><head><status>100</status></head><body><action servercode="4381" /></body></mtl>"""
  val xmlBonusAssigned = """<mtl><head><status>100</status></head><body><action msg="Success"><AssignedUserBonusList><AssignedUserBonus><UserGUID>p1</UserGUID><BonusId>b1</BonusId></AssignedUserBonus></AssignedUserBonusList></action></body></mtl>"""

  val mockClientHandler: Handler[MockClientBehavior, Throwable, (Path, Request), Response] =
    Handler.fromFunctionZIO { case (_, req) =>
      for {
        body       <- req.body.asString
        behavior   <- ZIO.service[MockClientBehavior]
        shouldFail <- behavior.failOnLogin.get
      } yield {
        if (body.contains("A.GE.2.2")) { //login request
          if (shouldFail) Response.text(xmlLoginFailed)
          else Response.text(xmlLoginSuccess)
        }
        else if (body.contains("<sessioncode>EXPIRED_SESSION</sessioncode>")) {
          Response.text(xmlSessionExpired)
        }
        else if (body.contains("<sessioncode>FRESH_SESSION</sessioncode>")) {
          Response.text(xmlBonusAssigned)
        }
        else {
          Response.error(Status.BadRequest)
        }
      }
    }

  val mockClientLayer: ZLayer[Scope & MockClientBehavior, Nothing, Client] =
    TestClient.layer >>> ZLayer.fromZIO {
      for {
        behavior <- ZIO.service[MockClientBehavior]
        handlerWithEnv = mockClientHandler.provideEnvironment(ZEnvironment(behavior))

        _ <- TestClient.addRoutes(
          Routes(Method.ANY / trailing -> handlerWithEnv).sandbox
        )

        client <- ZIO.service[Client]
      } yield client
    }

  val configLayer = ZLayer.succeed(BonusClientConfig(url = "http://mock", appCode = "TEST", lang = "en", timeoutSeconds = 5))

  val repoLayer: ZLayer[Any, Nothing, BonusApiSessionRepository] =
    ZLayer.fromZIO(
      for {
        data  <- Ref.make(Option.empty[BonusApiSession])
        calls <- Ref.make(0)
      } yield new InMemorySessionRepo(data, calls)
    )

  val fullLayer = ZLayer.make[BonusApiClient & BonusApiSessionRepository & MockClientBehavior & Scope](
    configLayer,
    mockClientLayer,
    repoLayer,
    MockClientBehavior.layer,
    BonusXmlApiClient.layer,
    Scope.default
  )


  def spec = suite("BonusXmlApiClient Orchestration")(

    test("Happy Path: Uses DB cached session if valid") {
      val playerId = PlayerId("p1")
      val bonusCode = BonusCode("b1")

      for {
        // Setup: repo has a FRESH session already
        repo <- ZIO.service[BonusApiSessionRepository]
        _    <- repo.saveSession(BonusApiSession(BonusApiSessionCode("FRESH_SESSION"), java.time.Instant.now))

        // Execute
        client <- ZIO.service[BonusApiClient]
        result <- client.assignBonus(playerId, bonusCode)
        savedSession <- repo.getSession

      } yield assertTrue(
        result.status == BonusAssignmentResultStatus.Assigned,
        savedSession.exists(_.sessionCode.value == "FRESH_SESSION")
      )
    },

    test("Login Flow: Successfully logs in when no session exists") {
      val playerId = PlayerId("p1")
      val bonusCode = BonusCode("b1")

      for {
        // Setup: empty repo
        repo <- ZIO.service[BonusApiSessionRepository]
        _    <- repo.asInstanceOf[InMemorySessionRepo].clear

        // Execute
        client <- ZIO.service[BonusApiClient]
        result <- client.assignBonus(playerId, bonusCode)

        savedSession <- repo.getSession
      } yield assertTrue(
        result.status == BonusAssignmentResultStatus.Assigned,
        savedSession.exists(_.sessionCode.value == "FRESH_SESSION")
      )
    },

    test("Login Failure: Returns SessionError when API returns invalid session code") {
      val playerId = PlayerId("p1")
      val bonusCode = BonusCode("b1")

      for {
        // Setup: Empty repo
        repo <- ZIO.service[BonusApiSessionRepository]
        _    <- repo.asInstanceOf[InMemorySessionRepo].clear

        // Setup: Force the Mock to fail login
        behavior <- ZIO.service[MockClientBehavior]
        _        <- behavior.failOnLogin.set(true)

        client <- ZIO.service[BonusApiClient]

        // Execute: Should fail during the internal login step
        result <- client.assignBonus(playerId, bonusCode).exit
      } yield assertTrue(
        result.isFailure,
        result.causeOption.get.failures.head.isInstanceOf[BonusApiError.SessionError],
        result.causeOption.get.failures.head.getMessage.contains("Status: 401")
      )
    },

    test("Session Refresh Flow: Recovers from 4381 error if DB cached session is expired") {
      val playerId = PlayerId("p1")
      val bonusCode = BonusCode("b1")

      for {
        // Setup: Repo has an EXPIRED session
        repo <- ZIO.service[BonusApiSessionRepository]
        _    <- repo.saveSession(BonusApiSession(BonusApiSessionCode("EXPIRED_SESSION"), java.time.Instant.now))

        client <- ZIO.service[BonusApiClient]

        // Execute: This should trigger the full retry loop:
        // 1. Try with EXPIRED_SESSION -> Fail 4381
        // 2. Call Login -> Get FRESH_SESSION
        // 3. Retry with FRESH_SESSION -> Bonus Assigned
        result <- client.assignBonus(playerId, bonusCode)

        savedSession <- repo.getSession
      } yield assertTrue(
        result.status == BonusAssignmentResultStatus.Assigned,
        savedSession.exists(_.sessionCode.value == "FRESH_SESSION")
      )
    },

    test("Local Cache Optimization: Uses internal cache without calling Repo on second request") {
      val playerId = PlayerId("p1")
      val bonusCode = BonusCode("b1")

      for {
        repo   <- ZIO.service[BonusApiSessionRepository]
        client <- ZIO.service[BonusApiClient]

        _ <- repo.saveSession(BonusApiSession(BonusApiSessionCode("FRESH_SESSION"), java.time.Instant.now))
        // First call should hit the DB (Repo) to fetch the session
        _ <- client.assignBonus(playerId, bonusCode)

        // Reset the read counter
        testRepo = repo.asInstanceOf[InMemorySessionRepo]
        _ <- testRepo.sessionReadCounter.set(0)

        // Clear session in DB to prove that it will not be used. If the client reads this, it would fail or use the wrong session.
        _ <- testRepo.clear

        // Second call should use in memory cached session
        result <- client.assignBonus(playerId, bonusCode)

        readsFromDb <- testRepo.sessionReadCounter.get
      } yield assertTrue(
        result.status == BonusAssignmentResultStatus.Assigned,
        readsFromDb == 0
      )
    }

  ).provide(fullLayer)
}