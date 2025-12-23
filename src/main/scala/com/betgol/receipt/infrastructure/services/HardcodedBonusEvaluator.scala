package com.betgol.receipt.infrastructure.services

import com.betgol.receipt.domain.Ids.BonusCode
import com.betgol.receipt.domain.services.BonusEvaluator
import zio.ZLayer


object HardcodedBonusEvaluator extends BonusEvaluator {

  override def evaluate(amount: BigDecimal): Option[BonusCode] =
    if (amount < 10.0) None
      else if (amount <= 50.0) Some(BonusCode("10_FREE_SPINS"))
      else Some(BonusCode("20_FREE_SPINS"))

  val layer: ZLayer[Any, Nothing, BonusEvaluator] = ZLayer.succeed(this)
}