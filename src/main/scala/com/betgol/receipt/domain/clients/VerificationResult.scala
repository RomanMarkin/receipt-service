package com.betgol.receipt.domain.clients


case class VerificationResult(status: VerificationResultStatus,
                              description: Option[String] = None,
                              externalId: Option[String] = None)


sealed trait VerificationResultStatus
object VerificationResultStatus {
  case object Valid extends VerificationResultStatus
  case object NotFound extends VerificationResultStatus
  case object Annulled extends VerificationResultStatus
}
