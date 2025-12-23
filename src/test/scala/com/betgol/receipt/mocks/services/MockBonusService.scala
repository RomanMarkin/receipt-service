package com.betgol.receipt.mocks.services

import com.betgol.receipt.config.{BonusClientConfig, BonusServiceConfig}
import com.betgol.receipt.domain.clients.BonusApiClient
import com.betgol.receipt.domain.repos.BonusAssignmentRepository
import com.betgol.receipt.domain.services.{BonusEvaluator, BonusService, BonusServiceLive, IdGenerator}
import com.betgol.receipt.mocks.clients.MockBonusApiClient
import zio.ZLayer


object MockBonusService {
  private val mockConfig = BonusServiceConfig(
    maxRetries = 5,
    bettingClient = BonusClientConfig(
      url = "https://not-used-mock-url",
      token = "not-used-mock-token",
      timeoutSeconds = 10
    )
  )

  val layer: ZLayer[IdGenerator & BonusEvaluator & BonusAssignmentRepository & BonusApiClient, Nothing, BonusService] =
    ZLayer.succeed(mockConfig) >+>
    BonusServiceLive.layer

  val bonusAssignedPath: ZLayer[IdGenerator & BonusAssignmentRepository, Nothing, BonusService] = {
    MockBonusApiClient.bonusAssignedPath >+>
    MockBonusEvaluator.bonusAvailablePath >+>
    MockBonusService.layer
  }
}