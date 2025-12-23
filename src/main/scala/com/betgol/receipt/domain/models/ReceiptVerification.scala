package com.betgol.receipt.domain.models

import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId, VerificationId}

import java.time.Instant


case class ReceiptVerification(id: VerificationId,
                               submissionId: SubmissionId,
                               playerId: PlayerId,
                               status: ReceiptVerificationStatus,
                               country: CountryCode,
                               attempts: List[ReceiptVerificationAttempt],
                               createdAt: Instant,
                               updatedAt: Instant)

object ReceiptVerification {
  def initial(id: VerificationId,
              submissionId: SubmissionId,
              playerId: PlayerId,
              country: CountryCode,
              now: Instant): ReceiptVerification =
    ReceiptVerification(id = id,
      submissionId = submissionId,
      playerId = playerId,
      status = ReceiptVerificationStatus.Pending,
      country = country,
      attempts = List.empty,
      createdAt = now,
      updatedAt = now)
}

case class ReceiptVerificationAttempt(status: ReceiptVerificationAttemptStatus,
                                      attemptNumber: Int,
                                      attemptedAt: Instant,
                                      provider: Option[String],
                                      description: Option[String])

enum ReceiptVerificationStatus {
  case Pending
  case RetryScheduled
  case Confirmed
  case Annulled
  case Exhausted
}

enum ReceiptVerificationAttemptStatus {
  case Valid
  case NotFound
  case Annulled
  case Failed
  case SystemError
}