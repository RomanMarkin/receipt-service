package com.betgol.receipt.domain.services

import com.betgol.receipt.domain.BonusCode


trait BonusEvaluator {
  def evaluate(amount: Double): Option[BonusCode]
}