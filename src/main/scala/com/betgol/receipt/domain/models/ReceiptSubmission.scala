package com.betgol.receipt.domain.models

import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.*

import java.time.{Instant, LocalDate}


case class ReceiptSubmission(id: SubmissionId,
                             status: SubmissionStatus,
                             metadata: SubmissionMetadata,
                             fiscalDocument: Option[FiscalDocument],
                             verification: Option[VerificationOutcome],
                             bonus: Option[BonusOutcome],
                             failureReason: Option[String])

object ReceiptSubmission {
  def validSubmission(id: SubmissionId, playerId: PlayerId, country: CountryCode, rawInput: String, fiscalDocument: FiscalDocument, now: Instant): ReceiptSubmission =
    ReceiptSubmission(id = id,
      status = SubmissionStatus.VerificationPending,
      metadata = SubmissionMetadata(playerId, country, submittedAt = now, rawInput),
      fiscalDocument = Some(fiscalDocument),
      verification = None,
      bonus = None,
      failureReason = None)

  def invalidSubmission(id: SubmissionId, playerId: PlayerId, country: CountryCode, rawInput: String, failureReason: String, now: Instant): ReceiptSubmission =
    ReceiptSubmission(
      id = id,
      status = SubmissionStatus.InvalidReceiptData,
      metadata = SubmissionMetadata(playerId, country, submittedAt = now, rawInput),
      fiscalDocument = None,
      verification = None,
      bonus = None,
      failureReason = Some(failureReason))
}

case class SubmissionMetadata(playerId: PlayerId,
                              country: CountryCode,
                              submittedAt: Instant,
                              rawInput: String)

case class FiscalDocument(issuerTaxId: String,
                          docType: String,
                          series: String,
                          number: String,
                          totalAmount: BigDecimal,
                          issuedAt: LocalDate)

case class VerificationOutcome(status: ReceiptVerificationStatus,
                               statusDescription: Option[String],
                               apiProvider: Option[String],
                               updatedAt: Instant,
                               externalId: Option[String])

case class BonusOutcome(status: BonusAssignmentStatus,
                        statusDescription: Option[String],
                        code: Option[BonusCode],
                        updatedAt: Instant,
                        externalId: Option[String])

object BonusOutcome {
  def apply(status: BonusAssignmentStatus,
            updatedAt: Instant): BonusOutcome = 
    BonusOutcome(code = None, status = status, statusDescription = None, updatedAt = updatedAt, externalId = None)
}


enum SubmissionStatus {
  case InvalidReceiptData
  case VerificationPending
  case VerificationRejected
  case VerificationFailed
  case Verified
  case NoBonusAvailable
  case BonusAssignmentPending
  case BonusAssignmentRejected
  case BonusAssignmentFailed
  case BonusAssigned
}