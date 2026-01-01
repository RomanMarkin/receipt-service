package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Ids.BonusApiSessionCode

import java.time.Instant


case class BonusAssignmentResult(status: BonusAssignmentResultStatus,
                                 description: Option[String] = None,
                                 externalId: Option[String] = None)

// This status enum is retained to provide flexibility for possible future improvements
// (e.g. handling specific partial states or non-binary outcomes).
sealed trait BonusAssignmentResultStatus
object BonusAssignmentResultStatus {
  case object Assigned extends BonusAssignmentResultStatus
}

case class BonusApiSession(sessionCode: BonusApiSessionCode,
                           updatedAt: Instant)