package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.BonusCode
import com.betgol.receipt.domain.Ids.SubmissionId
import zio.IO


trait BettingApiClient {
  def assignBonus(playerId: String, bonusCode: BonusCode, submissionId: SubmissionId): IO[BettingApiError, Unit]
}
