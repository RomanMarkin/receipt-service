package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.VerificationId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationStatus}
import zio.IO

import java.time.Instant


trait ReceiptVerificationRepository {
  def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId]
  def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus, nextRetryAt: Option[Instant]): IO[RepositoryError, Unit]
  def findReadyForRetry(now: Instant, limit: Int): IO[RepositoryError, List[ReceiptVerification]]
}