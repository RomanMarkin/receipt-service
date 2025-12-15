package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.BonusCode
import com.betgol.receipt.domain.Ids.SubmissionId
import com.betgol.receipt.domain.clients.{BettingApiClient, BettingApiError}
import zio.{IO, ZIO, ZLayer}


class MockBettingXmlApiClient() extends BettingApiClient {

  override def assignBonus(playerId: String, bonusCode: BonusCode, subscriptionId: SubmissionId): IO[BettingApiError, Unit] =
    ZIO.succeed(())
}

object MockBettingXmlApiClient {
  val layer: ZLayer[Any, Nothing, BettingApiClient] =
    ZLayer.succeed(new MockBettingXmlApiClient())
}
