package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.SubmissionId
import com.betgol.receipt.domain.{ReceiptSubmission, ReceiptSubmissionError, VerificationConfirmation}
import zio.IO


trait ReceiptSubmissionRepository {
  def add(rs: ReceiptSubmission): IO[ReceiptSubmissionError, SubmissionId]
  def updateConfirmed(submissionId: SubmissionId, verification: VerificationConfirmation): IO[ReceiptSubmissionError, Unit]
}
