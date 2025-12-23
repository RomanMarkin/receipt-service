package com.betgol.receipt.domain.services

import com.betgol.receipt.domain.Ids.BonusCode


trait BonusEvaluator {
  def evaluate(amount: BigDecimal): Option[BonusCode]
}