package com.betgol.receipt.jobs

import com.betgol.receipt.domain.ReceiptSubmissionError
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.{ReceiptSubmissionRepository, ReceiptVerificationJobStatsRepository, ReceiptVerificationRepository}
import com.betgol.receipt.services.ReceiptService
import zio.*


object ReceiptVerificationRetryJob {

  def run: ZIO[ReceiptService & ReceiptSubmissionRepository & ReceiptVerificationRepository & ReceiptVerificationJobStatsRepository, ReceiptSubmissionError, ReceiptVerificationJobStats] =
    for {
      start <- Clock.instant
      _ <- ZIO.logInfo("Starting Receipt Verification Retry Job...")

      candidates <- ZIO.serviceWithZIO[ReceiptVerificationRepository](_.findReadyForRetry(start, limit = 50))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to retrieve receipt verifications for retry: ${e.getMessage}", e))

      statsRef <- Ref.make(ReceiptVerificationJobStats(start))

      _ <- ZIO.foreachPar(candidates) { receiptVerification =>
        processRetry(receiptVerification).tap { submissionStatus =>
          statsRef.update(currentStats =>
            val base = currentStats.copy(processed = currentStats.processed + 1)
            submissionStatus match {
              case SubmissionStatus.VerificationPending => base.copy(rescheduled = base.rescheduled + 1)
              case SubmissionStatus.VerificationRejected => base.copy(rejected = base.rejected + 1)
              case SubmissionStatus.VerificationFailed => base.copy(failed = base.failed + 1)
              case _ => base.copy(succeeded = base.succeeded + 1) // all other submission statuses mean successful verification
            }
          )
        }
      }.withParallelism(5)

      finalStats <- statsRef.get
      _ <- ZIO.serviceWithZIO[ReceiptVerificationJobStatsRepository](_.add(finalStats))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to add VerificationJobStats: $finalStats. Cause: ${e.getMessage}", e))

      _ <- ZIO.logInfo(s"Finished Receipt Verification Retry Job. Stats: $finalStats")
    } yield finalStats

  private def processRetry(v: ReceiptVerification): ZIO[ReceiptService & ReceiptSubmissionRepository & ReceiptVerificationRepository, ReceiptSubmissionError, SubmissionStatus] =
    for {
      submission <- ZIO.serviceWithZIO[ReceiptSubmissionRepository](_.getById(v.submissionId))
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to get submission for verification: ${v.submissionId}. Cause: ${e.getMessage}", e))

      fiscalDocument <- ZIO.fromOption(submission.fiscalDocument)
        .orElseFail(ReceiptSubmissionError.SystemError(s"Data Integrity Error: ReceiptSubmission ${v.submissionId} has no fiscal document enclosed."))

      submissionResult <- ZIO.serviceWithZIO[ReceiptService](_.handleReceiptVerification(
        v.submissionId, v.playerId, v.country, fiscalDocument, attemptNumber = v.attempts.size + 1, Some(v.id)
      ))
    } yield submissionResult.status
}