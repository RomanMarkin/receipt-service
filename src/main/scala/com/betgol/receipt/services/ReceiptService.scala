package com.betgol.receipt.services

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, CountryCode, PlayerId, SubmissionId, VerificationId}
import com.betgol.receipt.domain.ReceiptSubmissionError.toSystemError
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.ReceiptSubmissionRepository
import com.betgol.receipt.domain.services.{BonusService, IdGenerator, VerificationService}
import com.betgol.receipt.services.ReceiptServiceLive.*
import zio.*


trait ReceiptService {
  /** Entry point for entire lifecycle of receipt submission. Used by controller. */
  def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult]

  /** Entry point for receipt verification retry job. */
  def handleReceiptVerification(submissionId: SubmissionId,
                                playerId: PlayerId,
                                country: CountryCode,
                                fiscalDocument: FiscalDocument,
                                attemptNumber: Int,
                                verificationId: Option[VerificationId] = None): IO[ReceiptSubmissionError, ReceiptSubmissionResult]

  /** Entry point for bonus assignment retry job. */
  def handleBonusAssignment(submissionId: SubmissionId,
                            playerId: PlayerId,
                            doc: FiscalDocument,
                            attemptNumber: Int,
                            assignmentId: Option[BonusAssignmentId] = None,
                            bonusCode: Option[BonusCode] = None): IO[ReceiptSubmissionError, ReceiptSubmissionResult]
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
      result <- handleReceiptVerification(id, cmd.playerId, cmd.country, fiscalDocument, attemptNumber = 1)
    } yield result

  override def handleReceiptVerification(submissionId: SubmissionId,
                                         playerId: PlayerId,
                                         country: CountryCode,
                                         fiscalDocument: FiscalDocument,
                                         attemptNumber: Int,
                                         verificationId: Option[VerificationId]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] = {

    val verificationEffect: IO[ReceiptSubmissionError, VerificationOutcome] =
      if (attemptNumber == 1) {
        verificationService.initiate(submissionId, playerId, country, fiscalDocument)
      } else {
        verificationId match {
          case Some(vId) =>
            verificationService.executeAttempt(vId, submissionId, country, fiscalDocument, attemptNumber)
          case None =>
            ZIO.fail(ReceiptSubmissionError.SystemError(s"Verification retry (attempt $attemptNumber) called without VerificationId for submission $submissionId"))
        }
      }

    for {
      vOutcome <- verificationEffect

      submissionStatus = vOutcome.status.toSubmissionStatus
      _ <- repo.updateVerificationOutcome(submissionId, submissionStatus, vOutcome)
        .mapError(_.toSystemError)

      result <- submissionStatus match {
        case SubmissionStatus.Verified =>
          ZIO.logInfo(s"Receipt $submissionId verified (Attempt $attemptNumber). Proceeding to bonus.") *>
            handleBonusAssignment(submissionId, playerId, fiscalDocument, attemptNumber = 1)

        case SubmissionStatus.VerificationPending =>
          ZIO.logInfo(s"Receipt $submissionId verification pending (Attempt $attemptNumber).") *>
            ZIO.succeed(ReceiptSubmissionResult(submissionId, SubmissionStatus.VerificationPending))

        case status =>
          val msg = vOutcome.statusDescription.getOrElse("Verification rejected")
          ZIO.logWarning(s"Receipt $submissionId verification failed. Status: $status.") *>
            ZIO.succeed(ReceiptSubmissionResult(submissionId, status, Some(msg)))
      }
    } yield result
  }

  override def handleBonusAssignment(submissionId: SubmissionId,
                                     playerId: PlayerId,
                                     doc: FiscalDocument,
                                     attemptNumber: Int,
                                     assignmentId: Option[BonusAssignmentId],
                                     bonusCode: Option[BonusCode]): IO[ReceiptSubmissionError, ReceiptSubmissionResult] = {

    val bonusEffect: IO[ReceiptSubmissionError, BonusOutcome] =
      if (attemptNumber == 1) {
        bonusService.initiate(submissionId, playerId, doc)
      } else {
        (assignmentId, bonusCode) match {
          case (Some(bId), Some(bCode)) =>
            bonusService.executeAttempt(bId, playerId, bCode, attemptNumber)
          case _ =>
            ZIO.fail(ReceiptSubmissionError.SystemError(s"Retry (Attempt $attemptNumber) called without BonusAssignmentId or bonus code for submission $submissionId"))
        }
      }

    for {
      bOutcome <- bonusEffect

      submissionStatus = bOutcome.status.toSubmissionStatus
      _ <- repo.updateBonusOutcome(submissionId, submissionStatus, bOutcome)
        .mapError(_.toSystemError)

      _ <- ZIO.logInfo(s"Bonus assignment for $submissionId completed: $submissionStatus")

    } yield ReceiptSubmissionResult(submissionId, submissionStatus, bOutcome.code.map(c => s"Bonus Code: ${c.value}"))
  }

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