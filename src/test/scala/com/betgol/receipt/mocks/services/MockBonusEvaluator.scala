package com.betgol.receipt.mocks.services

import com.betgol.receipt.domain.Ids.BonusCode
import com.betgol.receipt.domain.services.BonusEvaluator
import zio.ZLayer


class MockBonusEvaluator(isBonusAvailable: Boolean) extends BonusEvaluator {
  def evaluate(amount: BigDecimal): Option[BonusCode] =
    if (isBonusAvailable) {
      Some(BonusCode("TEST_BONUS"))
    } else {
      None
    }
}

object MockBonusEvaluator {
  val bonusAvailablePath: ZLayer[Any, Nothing, BonusEvaluator] =
    ZLayer.succeed(new MockBonusEvaluator(true))

  val bonusNotAvailablePath: ZLayer[Any, Nothing, BonusEvaluator] =
    ZLayer.succeed(new MockBonusEvaluator(false))
}