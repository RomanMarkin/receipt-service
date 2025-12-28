package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Ids.{BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.{BonusApiClient, BonusApiError, BonusAssignmentResult, BonusAssignmentResultStatus}
import zio.{IO, ZIO, ZLayer}


class MockBonusApiClient(outcome: Either[BonusApiError, BonusAssignmentResult]) extends BonusApiClient {
  
  override def assignBonus(playerId: PlayerId, bonusCode: BonusCode): IO[BonusApiError, BonusAssignmentResult] =
    ZIO.fromEither(outcome)
}

object MockBonusApiClient {

  val bonusAssignedPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(
      Right(BonusAssignmentResult(
        status = BonusAssignmentResultStatus.Assigned,
        description = Some("Mock Bonus Assigned"),
        externalId = Some("mock-external-id")
      ))
    ))

  val bonusRejectedPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(
      Left(BonusApiError.BonusRejected("Mock Rejection: User ineligible"))
    ))

  val networkErrorPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(
      Left(BonusApiError.NetworkError("Mock Network Failure"))
    ))

  val systemErrorPath: ZLayer[Any, Nothing, BonusApiClient] =
    ZLayer.succeed(new MockBonusApiClient(
      Left(BonusApiError.SystemError("Mock System Failure"))
    ))
}