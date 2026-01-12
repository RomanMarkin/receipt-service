package com.betgol.receipt.domain.services

import com.betgol.receipt.config.BonusServiceConfig
import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, PlayerId, SubmissionId}
import com.betgol.receipt.domain.clients.{BonusApiClient, BonusApiError}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.BonusAssignmentRepository
import com.betgol.receipt.domain.services.BonusServiceLive.toBonusAssignmentStatus
import zio.*


trait BonusService {
  /** Starts the bonus assignment lifecycle. Checks bonus eligibility, creates BonusAssignment entity, and performs the first attempt. */
  def initiate(submissionId: SubmissionId, playerId: PlayerId, fiscalDocument: FiscalDocument): IO[ReceiptSubmissionError, BonusOutcome]

  /** Execution single assignment attempt. Can be called by 'initiate' (on first attempt) or the Retry Job (attempts > 1). */
  def executeAttempt(assignmentId: BonusAssignmentId, playerId: PlayerId, bonusCode: BonusCode, currentAttempt: Int): IO[ReceiptSubmissionError, BonusOutcome]
}

case class BonusServiceLive(config: BonusServiceConfig,
                            idGenerator: IdGenerator,
                            bonusEvaluator: BonusEvaluator,
                            retryPolicy: RetryPolicy,
                            repo: BonusAssignmentRepository,
                            bettingClient: BonusApiClient) extends BonusService {

  override def initiate(submissionId: SubmissionId, playerId: PlayerId, fiscalDocument: FiscalDocument): IO[ReceiptSubmissionError, BonusOutcome] =
    bonusEvaluator.evaluate(fiscalDocument.totalAmount.toDouble) match {
      case None =>
        for {
          now <- Clock.instant
          bonusOutcome <- ZIO.succeed(BonusOutcome(status = BonusAssignmentStatus.NoBonus, updatedAt = now))
        } yield bonusOutcome

      case Some(bonusCode) =>
        for {
          assignmentId <- prepareBonusAssignment(submissionId, playerId, bonusCode)
          bonusOutcome <- executeAttempt(assignmentId, playerId, bonusCode, currentAttempt = 1)
        } yield bonusOutcome
    }

  override def executeAttempt(assignmentId: BonusAssignmentId, playerId: PlayerId, bonusCode: BonusCode, currentAttempt: Int): IO[ReceiptSubmissionError, BonusOutcome] =
    for {
      exitResult <- bettingClient.assignBonus(playerId, bonusCode).exit

      (attemptStatus, description, externalId) = exitResult match {
        case Exit.Success(res) =>
          (BonusAssignmentAttemptStatus.Success, res.description, res.externalId)
        case Exit.Failure(cause) if cause.isFailure =>
          cause.failureOption match {
            case Some(e: BonusApiError.BonusRejected) =>
              (BonusAssignmentAttemptStatus.Rejected, Some(e.getMessage), None)
            case Some(e) =>
              (BonusAssignmentAttemptStatus.SystemError, Some(e.getMessage), None)
            case None =>
              (BonusAssignmentAttemptStatus.SystemError, Some("Unknown failure"), None)
          }
        case Exit.Failure(cause) =>
          (BonusAssignmentAttemptStatus.SystemError, Some(s"Critical Crash: ${cause.prettyPrint}"), None)
      }

      now <- Clock.instant
      attempt = BonusAssignmentAttempt(attemptStatus, currentAttempt, now, description)
      assignmentStatus = attemptStatus.toBonusAssignmentStatus(currentAttempt, config.maxRetries)

      nextRetryAt <- retryPolicy.nextRetryTimestamp(assignmentStatus, BonusAssignmentStatus.RetryScheduled, currentAttempt)
      
      _ <- repo.addAttempt(assignmentId, attempt, assignmentStatus, nextRetryAt)
        .mapError(dbErr => ReceiptSubmissionError.SystemError(s"Failed to save bonus attempt: ${dbErr.getMessage}"))

    } yield BonusOutcome(
      code = Some(bonusCode),
      status = assignmentStatus,
      statusDescription = description,
      updatedAt = now,
      externalId = externalId)

  private def prepareBonusAssignment(submissionId: SubmissionId, playerId: PlayerId, bonusCode: BonusCode): IO[ReceiptSubmissionError, BonusAssignmentId] =
    for {
      id <- idGenerator.generate.map(BonusAssignmentId(_))
      now <- Clock.instant
      assignment = BonusAssignment.initial(id, submissionId, playerId, bonusCode, now)
      _ <- repo.add(assignment).mapError(e => ReceiptSubmissionError.SystemError(s"Failed to save BonusAssignment (id: ${id.value}): ${e.getMessage}"))
    } yield id
}

object BonusServiceLive {
  val layer: ZLayer[BonusServiceConfig & IdGenerator & BonusEvaluator & RetryPolicy & BonusAssignmentRepository & BonusApiClient, Nothing, BonusService] = {
    ZLayer.fromFunction(BonusServiceLive.apply _)
  }

  extension (attemptStatus: BonusAssignmentAttemptStatus) {
    def toBonusAssignmentStatus(currentAttempt: Int, maxRetries: Int): BonusAssignmentStatus =
      attemptStatus match {
        case BonusAssignmentAttemptStatus.Success =>
          BonusAssignmentStatus.Assigned
        case BonusAssignmentAttemptStatus.Rejected =>
          BonusAssignmentStatus.Rejected
        case BonusAssignmentAttemptStatus.SystemError =>
          if (currentAttempt < maxRetries) BonusAssignmentStatus.RetryScheduled
          else BonusAssignmentStatus.Exhausted
      }
  }
}