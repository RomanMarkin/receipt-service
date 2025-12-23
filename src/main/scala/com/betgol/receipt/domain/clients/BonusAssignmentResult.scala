package com.betgol.receipt.domain.clients


case class BonusAssignmentResult(status: BonusAssignmentResultStatus,
                                 description: Option[String] = None,
                                 externalId: Option[String] = None)

sealed trait BonusAssignmentResultStatus
object BonusAssignmentResultStatus {
  case object Assigned extends BonusAssignmentResultStatus
  case object NotFound extends BonusAssignmentResultStatus
}