package com.betgol.receipt.domain

import com.betgol.receipt.domain.Ids.*

import java.time.{Instant, LocalDate}


case class ReceiptSubmission(id: SubmissionId,
                             status: SubmissionStatus,
                             metadata: SubmissionMetadata,
                             fiscalDocument: Option[FiscalDocument],
                             verification: Option[VerificationConfirmation],
                             bonus: Option[BonusOutcome],
                             failureReason: Option[String])

object ReceiptSubmission {
  def validSubmission(id: SubmissionId, playerId: PlayerId, rawInput: String, fiscalDocument: FiscalDocument): ReceiptSubmission =
    ReceiptSubmission(id = id,
      status = SubmissionStatus.VerificationPending,
      metadata = SubmissionMetadata(playerId, submittedAt = Instant.now, rawInput),
      fiscalDocument = Some(fiscalDocument),
      verification = None,
      bonus = None,
      failureReason = None)

  def invalidSubmission(id: SubmissionId, playerId: PlayerId, rawInput: String, failureReason: String): ReceiptSubmission =
    ReceiptSubmission(
      id = id,
      status = SubmissionStatus.InvalidReceiptData,
      metadata = SubmissionMetadata(playerId, submittedAt = Instant.now, rawInput),
      fiscalDocument = None,
      verification = None,
      bonus = None,
      failureReason = Some(failureReason))
}

case class SubmissionMetadata(playerId: PlayerId,
                              submittedAt: Instant,
                              rawInput: String)

case class FiscalDocument(issuerTaxId: String,
                          docType: String,
                          series: String,
                          number: String,
                          totalAmount: BigDecimal,
                          issuedAt: LocalDate,
                          country: CountryCode = CountryCode("PE")) // TODO move it in controller. Temporary hardcoded to Peru for now

case class VerificationConfirmation(apiProvider: String,
                                    confirmedAt: Instant,
                                    externalId: Option[String],
                                    statusMessage: String)

case class BonusOutcome(code: BonusCode,
                        status: BonusAssignmentStatus,
                        assignedAt: Instant,
                        externalId: Option[String],
                        statusMessage: Option[String])

case class VerificationRetry(id: VerificationRetryId,
                             submissionId: SubmissionId,
                             playerId: PlayerId,
                             attempt: Int,
                             addedAt: Instant,
                             country: CountryCode,
                             status: VerificationRetryStatus)

object VerificationRetry {
  def initial(id: VerificationRetryId,
              submissionId: SubmissionId,
              playerId: PlayerId,
              country: CountryCode): VerificationRetry =
    VerificationRetry(id, submissionId, playerId, attempt = 1, addedAt = Instant.now, country, status = VerificationRetryStatus.Pending)
}

case class BonusCode(code: String) extends AnyVal

case class BonusAssignment(id: BonusAssignmentId,
                           submissionId: SubmissionId,
                           playerId: PlayerId,
                           bonusCode: BonusCode,
                           status: BonusAssignmentStatus,
                           attempt: Int,
                           createdAt: Instant,
                           lastAttemptAt: Option[Instant],
                           error: Option[String] = None)

enum SubmissionStatus {
  case InvalidReceiptData
  case VerificationPending
  case VerificationFailed
  case ValidatedNoBonus
  case BonusAssignmentPending
  case BonusAssigned
}

enum VerificationRetryStatus {
  case Pending
}

enum BonusAssignmentStatus {
  case Pending
  case Success
  case Failed
}