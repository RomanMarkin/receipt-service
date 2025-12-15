package com.betgol.receipt.services

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{SubmissionId, VerificationRetryId, *}
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.*
import com.betgol.receipt.domain.services.{BonusEvaluator, IdGenerator}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.mocks.clients.MockBettingXmlApiClient
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.{Instant, LocalDate}


object ReceiptServiceRaceSpec extends ZIOSpecDefault {


  case class DelayedMockClient(name: String,
                               delay: Duration,
                               result: Option[VerificationConfirmation]) extends FiscalApiClient {
    override def providerName: String = name

    override def verify(receipt: FiscalDocument): IO[FiscalApiError, Option[VerificationConfirmation]] = {
      ZIO.sleep(delay) *> ZIO.succeed(result) //ZIO.sleep() is using ZIO Clock, which is controlled by TestClock
    }
  }

  case class MockClientProvider(clients: List[FiscalApiClient]) extends FiscalClientProvider {
    def getClientsFor(countryIso: CountryCode): UIO[List[FiscalApiClient]] = ZIO.succeed(clients)
  }

  val dummyReceipt = FiscalDocument("123", "01", "F001", "1", 10.0, LocalDate.now(), CountryCode("PE"))

  // Mock dependencies
  val mockParser = new ReceiptParser {
    def parse(raw: String) = ZIO.succeed(dummyReceipt)
  }
  val mockReceiptSubmissionRepo = new ReceiptSubmissionRepository {
    override def add(rs: ReceiptSubmission): IO[ReceiptSubmissionError, SubmissionId] = ZIO.succeed(SubmissionId("id"))
    override def updateConfirmed(submissionId: SubmissionId, verification: VerificationConfirmation): IO[ReceiptSubmissionError, Unit] = ZIO.unit
  }
  val mockVerificationRetryRepo = new VerificationRetryRepository {
    def add(vr: VerificationRetry) = ZIO.succeed(VerificationRetryId("id"))
  }
  val mockBonusAssignmentRepo = new BonusAssignmentRepository {
    override def save(assignment: BonusAssignment): IO[BonusAssignmentError, Unit] = ZIO.unit
    override def updateStatus(id: BonusAssignmentId, status: BonusAssignmentStatus, error: Option[String]): IO[BonusAssignmentError, Unit] = ZIO.unit
  }

  def buildService(clients: List[FiscalApiClient]): ZLayer[Any, Nothing, ReceiptServiceLive] = {
    val dependencies =
      ZLayer.succeed(mockParser) ++
        ZLayer.succeed(mockReceiptSubmissionRepo) ++
        ZLayer.succeed(mockVerificationRetryRepo) ++
        ZLayer.succeed(mockBonusAssignmentRepo) ++
        ZLayer.succeed(MockClientProvider(clients)) ++
        ZLayer.succeed(new MockBettingXmlApiClient()) ++
        ZLayer.succeed(new UuidV7IdGenerator()) ++
        ZLayer.succeed(HardcodedBonusEvaluator)

    dependencies >>> ZLayer.fromFunction(ReceiptServiceLive.apply _)
  }

  override def spec = suite("ReceiptService Racing Logic")(

    test("Scenario 1: Fastest client wins") {
      val fastConfirmation = VerificationConfirmation("Fast", Instant.now(), None, "OK")
      val slowConfirmation = fastConfirmation.copy(apiProvider = "Slow")

      val clientFast = DelayedMockClient("Fast", 1.second, Some(fastConfirmation))
      val clientSlow = DelayedMockClient("Slow", 5.seconds, Some(slowConfirmation))

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(2.second) // advance time between the completion of Fast and Slow
        result <- fiber.join
      } yield assert(result)(isSome(equalTo(fastConfirmation))))
        .provide(buildService(List(clientFast, clientSlow)))
    },

    test("Scenario 2: If first client fails, second client wins") {
      val slowConfirmation = VerificationConfirmation("SlowButSuccess", Instant.now(), None, "OK")

      val clientFastFail = DelayedMockClient("FastFail", 100.millis, None)
      val clientSlowSuccess = DelayedMockClient("SlowSuccess", 2.seconds, Some(slowConfirmation))

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(2.second) // advance time after the completion of SlowSuccess
        result <- fiber.join
      } yield assert(result)(isSome(equalTo(slowConfirmation))))
        .provide(buildService(List(clientFastFail, clientSlowSuccess)))
    },

    test("Scenario 3: All clients are too slow (total timeout)") {
      val clientTooSlow1 = DelayedMockClient("Slow1", 10.seconds, None)
      val clientTooSlow2 = DelayedMockClient("Slow2", 10.seconds, None)

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(5.second)
        result <- fiber.join
      } yield assert(result)(isNone))
        .provide(buildService(List(clientTooSlow1, clientTooSlow2)))
    }
  )
}