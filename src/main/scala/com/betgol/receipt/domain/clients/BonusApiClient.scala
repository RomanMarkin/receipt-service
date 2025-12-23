package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Ids.PlayerId
import com.betgol.receipt.domain.Ids.BonusCode
import zio.IO


trait BonusApiClient {
  def assignBonus(playerId: PlayerId, bonusCode: BonusCode): IO[BonusApiError, BonusAssignmentResult]
}
