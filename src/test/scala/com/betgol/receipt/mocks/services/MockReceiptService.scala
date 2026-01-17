package com.betgol.receipt.mocks.services

import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, CountryCode, PlayerId, SubmissionId, VerificationId}
import com.betgol.receipt.domain.{ReceiptSubmissionError, ReceiptSubmissionResult, SubmitReceipt}
import com.betgol.receipt.domain.models.{FiscalDocument, SubmissionStatus}
import com.betgol.receipt.services.ReceiptService
import zio.{IO, ULayer, ZIO, ZLayer}


/**
 * A configurable mock which defaults to "Not Implemented" to fail fast if a test calls a method which was not configured.
 */
class MockReceiptService(
                          processLogic: SubmitReceipt => IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
                          _ => ZIO.dieMessage("process() not implemented in this mock configuration"),

                          verificationLogic: (SubmissionId, Option[VerificationId]) => IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
                          (_, _) => ZIO.dieMessage("handleReceiptVerification() not implemented"),

                          bonusLogic: (SubmissionId, Option[BonusAssignmentId]) => IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
                          (_, _) => ZIO.dieMessage("handleBonusAssignment() not implemented")
                        ) extends ReceiptService {

  override def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    processLogic(cmd)

  override def handleReceiptVerification(submissionId: SubmissionId, playerId: PlayerId, country: CountryCode, fiscalDocument: FiscalDocument, attemptNumber: Int, verificationId: Option[VerificationId]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    verificationLogic(submissionId, verificationId)

  override def handleBonusAssignment(submissionId: SubmissionId, playerId: PlayerId, doc: FiscalDocument, attemptNumber: Int, assignmentId: Option[BonusAssignmentId], bonusCode: Option[BonusCode]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    bonusLogic(submissionId, assignmentId)
}

object MockReceiptService {

  def successPath(status: SubmissionStatus): ULayer[ReceiptService] = {
    val successLogic = (id: SubmissionId) => ZIO.succeed(ReceiptSubmissionResult(id, status, None))

    ZLayer.succeed(new MockReceiptService(
      processLogic = _ => ZIO.succeed(ReceiptSubmissionResult(SubmissionId("new-mock-id"), status, None)),
      verificationLogic = (id, _) => successLogic(id),
      bonusLogic = (id, _) => successLogic(id)
    ))
  }

  def failure(error: ReceiptSubmissionError): ULayer[ReceiptService] =
    ZLayer.succeed(new MockReceiptService(
      processLogic = _ => ZIO.fail(error)
    ))

  def defect(t: Throwable): ULayer[ReceiptService] =
    ZLayer.succeed(new MockReceiptService(
      processLogic = _ => ZIO.die(t)
    ))

  def custom(logic: SubmitReceipt => IO[ReceiptSubmissionError, ReceiptSubmissionResult]): ULayer[ReceiptService] =
    ZLayer.succeed(new MockReceiptService(processLogic = logic))
}