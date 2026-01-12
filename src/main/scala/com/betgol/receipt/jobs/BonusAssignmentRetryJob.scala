package com.betgol.receipt.jobs

import com.betgol.receipt.domain.ReceiptSubmissionError
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.{BonusAssignmentJobStatsRepository, BonusAssignmentRepository, ReceiptSubmissionRepository}
import com.betgol.receipt.services.ReceiptService
import zio.*


object BonusAssignmentRetryJob {

  def run: ZIO[ReceiptService & ReceiptSubmissionRepository & BonusAssignmentRepository & BonusAssignmentJobStatsRepository, ReceiptSubmissionError, BonusAssignmentJobStats] =
    for {
      start <- Clock.instant
      _ <- ZIO.logInfo("Starting Bonus Assignment Retry Job...")

      candidates <- ZIO.serviceWithZIO[BonusAssignmentRepository](_.findReadyForRetry(start, limit = 50))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to retrieve bonus assignments for retry: ${e.getMessage}", e))

      statsRef <- Ref.make(BonusAssignmentJobStats(start))

      _ <- ZIO.foreachPar(candidates) { bonusAssignment =>
        processRetry(bonusAssignment).tap { submissionStatus =>
          statsRef.update(currentStats =>
            val base = currentStats.copy(processed = currentStats.processed + 1)
            submissionStatus match {
              case SubmissionStatus.BonusAssignmentPending => base.copy(rescheduled = base.rescheduled + 1)
              case SubmissionStatus.BonusAssignmentRejected => base.copy(rejected = base.rejected + 1)
              case SubmissionStatus.BonusAssignmentFailed => base.copy(failed = base.failed + 1)
              case SubmissionStatus.BonusAssigned => base.copy(succeeded = base.succeeded + 1)
              case _ => base // unexpected status
            }
          )
        }
      }.withParallelism(5)

      finalStats <- statsRef.get
      _ <- ZIO.serviceWithZIO[BonusAssignmentJobStatsRepository](_.add(finalStats))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to add BonusAssignmentJobStats: $finalStats. Cause: ${e.getMessage}", e))

      _ <- ZIO.logInfo(s"Finished Bonus Assignment Retry Job. Stats: $finalStats")
    } yield finalStats

  private def processRetry(a: BonusAssignment): ZIO[ReceiptService & ReceiptSubmissionRepository & BonusAssignmentRepository, ReceiptSubmissionError, SubmissionStatus] =
    for {
      submission <- ZIO.serviceWithZIO[ReceiptSubmissionRepository](_.getById(a.submissionId))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to get submission for verification: ${a.submissionId}. Cause: ${e.getMessage}", e))

      fiscalDocument <- ZIO.fromOption(submission.fiscalDocument)
        .orElseFail(ReceiptSubmissionError.SystemError(s"Data Integrity Error: ReceiptSubmission ${a.submissionId} has no fiscal document enclosed."))

      submissionResult <- ZIO.serviceWithZIO[ReceiptService](_.handleBonusAssignment(
        a.submissionId, a.playerId, fiscalDocument, attemptNumber = a.attempts.size + 1, Some(a.id), Some(a.bonusCode)
      ))
    } yield submissionResult.status
}