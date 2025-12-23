package com.betgol.receipt.infrastructure.clients.betting

import com.betgol.receipt.config.BonusClientConfig
import com.betgol.receipt.domain.Ids.{BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.{BonusApiClient, BonusApiError, BonusAssignmentResult, BonusAssignmentResultStatus, VerificationResultStatus}
import zio.http.{Client, URL}
import zio.{IO, ZIO, ZLayer}


case class BonusXmlApiClient(client: Client, config: BonusClientConfig, apiUrl: URL) extends BonusApiClient {

  override def assignBonus(playerId: PlayerId, bonusCode: BonusCode): IO[BonusApiError, BonusAssignmentResult] =
    ZIO.succeed(BonusAssignmentResult(status = BonusAssignmentResultStatus.Assigned, description = Some("Bonus Assigned"), externalId = Some("external-bonus-id")))
}

object BonusXmlApiClient {

  val layer: ZLayer[Client & BonusClientConfig, Nothing, BonusApiClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[BonusClientConfig]
        url <- ZIO.fromEither(URL.decode(config.url))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded ApiPeru URL: $e"))

      } yield BonusXmlApiClient(client, config, url)
    }
}
