package com.betgol.receipt.infrastructure.clients.betting

import com.betgol.receipt.config.BettingConfig
import com.betgol.receipt.domain.BonusCode
import com.betgol.receipt.domain.Ids.SubmissionId
import com.betgol.receipt.domain.clients.{BettingApiClient, BettingApiError}
import zio.http.{Client, URL}
import zio.{IO, ZIO, ZLayer}


case class BettingXmlApiClient(client: Client, apiUrl: URL, apiKey: String) extends BettingApiClient {

  override def assignBonus(playerId: String, bonusCode: BonusCode, subscriptionId: SubmissionId): IO[BettingApiError, Unit] =
    ??? //TODO impl
}

object BettingXmlApiClient {
  private val HardcodedUrl = "https://xxx.xxx/xx" //TODO move to config

  val layer: ZLayer[Client & BettingConfig, Nothing, BettingApiClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[BettingConfig]
        url <- ZIO.fromEither(URL.decode(HardcodedUrl))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded ApiPeru URL: $e"))

      } yield BettingXmlApiClient(client, url, config.token)
    }
}
