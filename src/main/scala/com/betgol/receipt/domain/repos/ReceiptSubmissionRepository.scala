package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.SubmissionId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusOutcome, ReceiptSubmission, SubmissionStatus, VerificationOutcome}
import zio.IO


trait ReceiptSubmissionRepository {
  def add(rs: ReceiptSubmission): IO[RepositoryError, SubmissionId]
  def updateVerificationOutcome(submissionId: SubmissionId, status: SubmissionStatus, verification: VerificationOutcome): IO[RepositoryError, Unit]
  def updateBonusOutcome(submissionId: SubmissionId, status: SubmissionStatus, bonus: BonusOutcome): IO[RepositoryError, Unit]
}
