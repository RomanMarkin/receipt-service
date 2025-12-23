package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.VerificationId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationStatus}
import zio.IO


trait ReceiptVerificationRepository {
  def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId]
  def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus): IO[RepositoryError, Unit]
}