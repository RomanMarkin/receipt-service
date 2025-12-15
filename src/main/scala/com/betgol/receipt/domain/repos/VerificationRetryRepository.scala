package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.VerificationRetryId
import com.betgol.receipt.domain.{ReceiptSubmissionError, VerificationRetry}
import zio.IO


trait VerificationRetryRepository {
  def add(vr: VerificationRetry): IO[ReceiptSubmissionError, VerificationRetryId]
}