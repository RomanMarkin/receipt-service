package com.betgol.receipt.mocks.services

import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, CountryCode, PlayerId, SubmissionId, VerificationId}
import com.betgol.receipt.domain.{ReceiptSubmissionError, ReceiptSubmissionResult, SubmitReceipt}
import com.betgol.receipt.domain.models.{FiscalDocument, SubmissionStatus}
import com.betgol.receipt.services.ReceiptService
import zio.{IO, ULayer, ZIO, ZLayer}


class MockReceiptService(resultStatus: SubmissionStatus) extends ReceiptService {
  override def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    ZIO.succeed(ReceiptSubmissionResult(
      id = SubmissionId("new-mock-id"),
      status = resultStatus,
      message = None))

  override def handleReceiptVerification(submissionId: SubmissionId, playerId: PlayerId, country: CountryCode, fiscalDocument: FiscalDocument, attemptNumber: Int, verificationId: Option[VerificationId]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    ZIO.succeed(ReceiptSubmissionResult(
      id = submissionId,
      status = resultStatus,
      message = None))

  override def handleBonusAssignment(submissionId: SubmissionId, playerId: PlayerId, doc: FiscalDocument, attemptNumber: Int , assignmentId: Option[BonusAssignmentId], bonusCode: Option[BonusCode]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    ZIO.succeed(ReceiptSubmissionResult(
      id = submissionId,
      status = resultStatus,
      message = None))
}

object MockReceiptService {
  def successPath(status: SubmissionStatus): ULayer[MockReceiptService] = ZLayer.succeed(new MockReceiptService(status))
}