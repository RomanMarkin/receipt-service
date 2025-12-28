package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Ids.BonusApiSessionCode

import java.time.Instant


case class BonusAssignmentResult(status: BonusAssignmentResultStatus,
                                 description: Option[String] = None,
                                 externalId: Option[String] = None)

//TODO status attribute delete ?
sealed trait BonusAssignmentResultStatus
object BonusAssignmentResultStatus {
  case object Assigned extends BonusAssignmentResultStatus
}

case class BonusApiSession(sessionCode: BonusApiSessionCode,
                           updatedAt: Instant)