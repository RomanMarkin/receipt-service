package com.betgol.receipt.infrastructure.clients.bonus

import com.betgol.receipt.config.BonusClientConfig
import com.betgol.receipt.domain.Ids.{BonusApiSessionCode, BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.repos.BonusApiSessionRepository
import zio.http.*
import zio.{Clock, IO, Ref, ZIO, ZLayer}


case class BonusXmlApiClient(client: Client,
                             config: BonusClientConfig,
                             apiUrl: URL,
                             sessionRepo: BonusApiSessionRepository,
                             localSessionCache: Ref[Option[BonusApiSessionCode]]) extends BonusApiClient {

  override def assignBonus(playerId: PlayerId, bonusCode: BonusCode): IO[BonusApiError, BonusAssignmentResult] = {

    def attemptAssignment(sessionCode: BonusApiSessionCode): IO[BonusApiError, BonusAssignmentResult] =
      for {
        responseXml <- executeBonusAssignment(sessionCode, playerId, bonusCode)
        result      <- BonusApiXml.parseBonusResponse(responseXml, playerId)
      } yield result

    for {
      currentSession <- ensureValidSession

      result <- attemptAssignment(currentSession).catchAll { error =>
        if (isSessionExpired(error)) {
          ZIO.logInfo(s"Bonus API Session expired (SessionCode: $currentSession). Refreshing...") *>
          recoverSession(badSession = currentSession).flatMap { newSession =>
            attemptAssignment(newSession)
          }
        } else {
          ZIO.fail(error)
        }
      }
    } yield result
  }

  private def ensureValidSession: IO[BonusApiError, BonusApiSessionCode] =
    localSessionCache.get.flatMap {
      case Some(cached) => ZIO.succeed(cached)
      case None         => fetchSessionFromDbOrLogin
    }

  private def fetchSessionFromDbOrLogin: IO[BonusApiError, BonusApiSessionCode] =
    sessionRepo.getSession
      .mapError(e => BonusApiError.SystemError(s"DB Error: ${e.getMessage}", e))
      .flatMap {
        case Some(s) => updateLocalCache(s.sessionCode)
        case None    => executeLogin
      }
  
  private def recoverSession(badSession: BonusApiSessionCode): IO[BonusApiError, BonusApiSessionCode] =
    for {
      _ <- localSessionCache.set(None)
      maybeDbSession <- sessionRepo.getSession.mapError(e => BonusApiError.SystemError(e.getMessage, e))
      validSession <- maybeDbSession match {
        case Some(s) if s.sessionCode != badSession =>
          ZIO.logInfo("Found fresh session code in DB, using it instead of Login.") *>
            updateLocalCache(s.sessionCode)
        case _ =>
          executeLogin
      }
    } yield validSession

  private def executeLogin: IO[BonusApiError, BonusApiSessionCode] =
    for {
      now         <- Clock.currentDateTime
      requestBody =  BonusApiXml.buildLoginRequest(config, now)
      response    <- sendRequest(requestBody)
      newCode     <- BonusApiXml.parseLoginResponse(response)
      _           <- sessionRepo.saveSession(BonusApiSession(newCode, now.toInstant))
        .mapError(e => BonusApiError.SystemError(s"DB Save Error: ${e.getMessage}", e))
      _           <- updateLocalCache(newCode)
    } yield newCode

  private def updateLocalCache(code: BonusApiSessionCode): IO[Nothing, BonusApiSessionCode] =
    localSessionCache.set(Some(code)).as(code)

  private def isSessionExpired(error: BonusApiError): Boolean = error match {
    case BonusApiError.SessionError(msg) => true
    case _ => false
  }
  
  private def executeBonusAssignment(sessionCode: BonusApiSessionCode, playerId: PlayerId, bonusId: BonusCode): IO[BonusApiError, String] =
    for {
      now        <- Clock.currentDateTime
      requestXml =  BonusApiXml.buildAssignmentRequest(config, sessionCode, now, playerId, bonusId)
      response   <- sendRequest(requestXml)
    } yield response

  private def sendRequest(xmlBody: String): IO[BonusApiError, String] =
    ZIO.scoped {
      client.request(
          Request.post(apiUrl, Body.fromString(xmlBody))
            .addHeader(Header.ContentType(MediaType.application.xml))
        )
        .mapError(e => BonusApiError.NetworkError(s"Network error: ${e.getMessage}", e))
        .flatMap { response =>
          response.body.asString
            .mapError(e => BonusApiError.NetworkError(s"Read error: ${e.getMessage}", e))
        }
    }
}

object BonusXmlApiClient {
  val layer: ZLayer[BonusClientConfig & BonusApiSessionRepository & Client, Nothing, BonusApiClient] =
    ZLayer {
      for {
        config <- ZIO.service[BonusClientConfig]
        url    <- ZIO.fromEither(URL.decode(config.url))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid Bonus API URL: $e"))
        client <- ZIO.service[Client]
        repo   <- ZIO.service[BonusApiSessionRepository]
        cache  <- Ref.make[Option[BonusApiSessionCode]](None)
      } yield BonusXmlApiClient(client, config, url, repo, cache)
    }
}