package com.betgol.receipt.services

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{PlayerId, SubmissionId}
import com.betgol.receipt.domain.ReceiptSubmissionError.toSystemError
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.ReceiptSubmissionRepository
import com.betgol.receipt.domain.services.{BonusService, IdGenerator, VerificationService}
import com.betgol.receipt.services.ReceiptServiceLive._
import zio.*


trait ReceiptService {
  def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult]
}

case class ReceiptServiceLive(idGenerator: IdGenerator,
                              parser: ReceiptParser,
                              repo: ReceiptSubmissionRepository,
                              verificationService: VerificationService,
                              bonusService: BonusService) extends ReceiptService {

  override def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult] = {
    parser.parse(cmd.receiptData).either.flatMap {
      case Right(parsedDoc) => handleValidReceipt(cmd, parsedDoc)
      case Left(errorMsg)   => handleInvalidReceipt(cmd, errorMsg)
    }
  }

  private def handleValidReceipt(cmd: SubmitReceipt, fiscalDocument: FiscalDocument): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    for {
      id  <- idGenerator.generate.map(SubmissionId(_))
      now <- Clock.instant
      submission = ReceiptSubmission.validSubmission(id, cmd.playerId, cmd.country, cmd.receiptData, fiscalDocument, now)
      _   <- addReceiptSubmission(submission)

      vOutcome <- verificationService.verify(id, cmd.playerId, cmd.country, fiscalDocument)

      submissionStatus = vOutcome.status.toSubmissionStatus
      _ <- repo.updateVerificationOutcome(id, submissionStatus, vOutcome)
        .mapError(_.toSystemError)
        .tapError(e => ZIO.logError(s"Failed to update verification outcome for $id: ${e.getMessage}"))

      result <- submissionStatus match {
        case SubmissionStatus.Verified =>
          ZIO.logInfo(s"Receipt $id verified successfully. Proceeding to bonus assignment.") *>
            handleBonusAssignment(id, cmd.playerId, fiscalDocument)

        case SubmissionStatus.VerificationPending =>
          ZIO.logInfo(s"Receipt $id verification pending retry.") *>
            ZIO.succeed(ReceiptSubmissionResult(id, SubmissionStatus.VerificationPending, Some("Verification pending retry")))

        case status =>
          val msg = vOutcome.statusDescription.getOrElse("Verification rejected or failed")
          ZIO.logWarning(s"Receipt $id verification failed. Status: $status. Reason: $msg") *>
            ZIO.succeed(ReceiptSubmissionResult(id, status, Some(msg)))
      }

    } yield result

  private def handleBonusAssignment(submissionId: SubmissionId, playerId: PlayerId, doc: FiscalDocument): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    for {
      bOutcome <- bonusService.evaluateAndApply(submissionId, playerId, doc)

      submissionStatus = bOutcome.status.toSubmissionStatus
      _ <- repo.updateBonusOutcome(submissionId, submissionStatus, bOutcome)
        .mapError(_.toSystemError)
        .tapError(e => ZIO.logError(s"Failed to update bonus outcome for $submissionId: ${e.getMessage}"))

      _ <- ZIO.logInfo(s"Bonus assignment for $submissionId completed: $submissionStatus")

    } yield ReceiptSubmissionResult(submissionId, submissionStatus)

  private def handleInvalidReceipt(cmd: SubmitReceipt, errorMsg: String): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    for {
      _   <- ZIO.logInfo(s"Invalid receipt data from ${cmd.playerId}: $errorMsg")
      id  <- idGenerator.generate.map(SubmissionId(_))
      now <- Clock.instant
      submission = ReceiptSubmission.invalidSubmission(id, cmd.playerId, cmd.country, cmd.receiptData, errorMsg, now)
      _   <- addReceiptSubmission(submission)
    } yield ReceiptSubmissionResult(id, SubmissionStatus.InvalidReceiptData, Some(errorMsg))

  private def addReceiptSubmission(s: ReceiptSubmission): IO[ReceiptSubmissionError, Unit] =
    repo.add(s).mapError {
      case e: RepositoryError.Duplicate => ReceiptSubmissionError.DuplicateReceipt(e.getMessage, e)
      case e                            => ReceiptSubmissionError.SystemError(e.getMessage, e)
    }.unit
}

object ReceiptServiceLive {
  val layer: ZLayer[
    IdGenerator & ReceiptParser & ReceiptSubmissionRepository & VerificationService & BonusService,
    Nothing,
    ReceiptService
  ] = ZLayer.fromFunction(ReceiptServiceLive.apply _)

  extension (vStatus: ReceiptVerificationStatus) {
    def toSubmissionStatus: SubmissionStatus = vStatus match {
      case ReceiptVerificationStatus.Confirmed      => SubmissionStatus.Verified
      case ReceiptVerificationStatus.Pending        => SubmissionStatus.VerificationPending
      case ReceiptVerificationStatus.RetryScheduled => SubmissionStatus.VerificationPending
      case ReceiptVerificationStatus.Annulled       => SubmissionStatus.VerificationRejected
      case ReceiptVerificationStatus.Exhausted      => SubmissionStatus.VerificationFailed
    }
  }

  extension (bStatus: BonusAssignmentStatus) {
    def toSubmissionStatus: SubmissionStatus = bStatus match {
      case BonusAssignmentStatus.NoBonus =>
        SubmissionStatus.NoBonusAvailable
      case BonusAssignmentStatus.Pending | BonusAssignmentStatus.RetryScheduled =>
        SubmissionStatus.BonusAssignmentPending
      case BonusAssignmentStatus.Assigned =>
        SubmissionStatus.BonusAssigned
      case BonusAssignmentStatus.Rejected =>
        SubmissionStatus.BonusAssignmentRejected
      case BonusAssignmentStatus.Exhausted =>
        SubmissionStatus.BonusAssignmentFailed
    }
  }
}