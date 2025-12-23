package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Ids.{BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.{BonusApiClient, BonusApiError, BonusAssignmentResult, BonusAssignmentResultStatus}
import zio.{IO, ZIO, ZLayer}


class MockBonusApiClient(resultStatus: BonusAssignmentResultStatus) extends BonusApiClient {

  override def assignBonus(playerId: PlayerId, bonusCode: BonusCode): IO[BonusApiError, BonusAssignmentResult] =
    val res = resultStatus match {
      case BonusAssignmentResultStatus.Assigned =>
        BonusAssignmentResult(status = resultStatus, description = Some("Mock Bonus Assigned"), externalId = Some("mock-external-id"))
      case BonusAssignmentResultStatus.NotFound =>
        BonusAssignmentResult(status = resultStatus, description = Some("Mock Bonus Not Found"), externalId = None)
    }
    ZIO.succeed(res)
}

object MockBonusApiClient {

  val bonusAssignedPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(BonusAssignmentResultStatus.Assigned))

  val bonusNotFoundPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(BonusAssignmentResultStatus.NotFound))
}