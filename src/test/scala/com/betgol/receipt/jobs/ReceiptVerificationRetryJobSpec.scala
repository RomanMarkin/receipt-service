package com.betgol.receipt.jobs

import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.ReceiptSubmissionError
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.*
import com.betgol.receipt.mocks.repos.MockReceiptSubmissionRepository
import com.betgol.receipt.mocks.services.MockReceiptService
import zio.*
import zio.test.*

import java.time.{Instant, ZoneId}


object ReceiptVerificationRetryJobSpec extends ZIOSpecDefault {

  // Test data fixtures
  val fixedNow: Instant = Instant.parse("2026-01-01T12:00:00Z")
  val submissionId: SubmissionId = SubmissionId("sub_1")
  val verificationId: VerificationId = VerificationId("ver_1")
  val playerId: PlayerId = PlayerId("player_1")
  val country: CountryCode = CountryCode("PE")

  val sampleVerification: ReceiptVerification = ReceiptVerification(
    id = verificationId,
    submissionId = submissionId,
    playerId = playerId,
    country = country,
    status = ReceiptVerificationStatus.RetryScheduled,
    attempts = List.empty,
    nextRetryAt = None,
    createdAt = fixedNow,
    updatedAt = fixedNow
  )

  val sampleFiscalDocument: FiscalDocument = FiscalDocument(
    issuerTaxId = "issuer_id_01",
    docType = "01",
    series = "F001",
    number = "00000000",
    totalAmount = BigDecimal(100),
    issuedAt = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate
  )

  val sampleSubmission: ReceiptSubmission = ReceiptSubmission(
    id = submissionId,
    status = SubmissionStatus.VerificationPending,
    metadata = SubmissionMetadata(
      playerId = playerId,
      country = country,
      submittedAt = fixedNow,
      rawInput = "raw_mock_receipt_data"
    ),
    fiscalDocument = Some(sampleFiscalDocument),
    verification = None,
    bonus = None,
    statusDescription = None
  )


  override def spec = suite("ReceiptVerificationRetryJobSpec")(

    test("runs successfully and counts 'succeeded' for all verified outcomes") {
      val successVerificationStatuses = List(
        SubmissionStatus.Verified,
        SubmissionStatus.NoBonusAvailable,
        SubmissionStatus.BonusAssignmentPending,
        SubmissionStatus.BonusAssignmentRejected,
        SubmissionStatus.BonusAssignmentFailed,
        SubmissionStatus.BonusAssigned
      )

      check(Gen.fromIterable(successVerificationStatuses)) { status =>
        val testLogic = for {
          _       <- TestClock.setTime(fixedNow)
          stats   <- ReceiptVerificationRetryJob.run
          ctx     <- ZIO.service[ReceiptVerificationTestContext]
          saved   <- ctx.savedStats.get
        } yield assertTrue(
          stats.processed == 1,
          stats.succeeded == 1,
          stats.failed == 0,
          stats.rejected == 0,
          stats.rescheduled == 0,

          saved.isDefined,
          saved.get.processed == 1
        )

        testLogic.provide(
          ReceiptVerificationTestContext.layer,
          MockReceiptService.successPath(status),
          MockReceiptSubmissionRepository.layer(sampleSubmission),
          MockReceiptVerificationRepo.layer(List(sampleVerification)),
          MockReceiptVerificationJobStatsRepo.layer
        )
      }
    },

    test("handles 'VerificationPending' status by rescheduling and counting 'rescheduled'") {
      for {
        _       <- TestClock.setTime(fixedNow)
        stats   <- ReceiptVerificationRetryJob.run
        ctx     <- ZIO.service[ReceiptVerificationTestContext]
        saved   <- ctx.savedStats.get
      } yield assertTrue(
        stats.processed == 1,
        stats.succeeded == 0,
        stats.failed == 0,
        stats.rejected == 0,
        stats.rescheduled == 1,

        saved.isDefined,
        saved.get.processed == 1
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.VerificationPending),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockReceiptVerificationRepo.layer(List(sampleVerification)),
      MockReceiptVerificationJobStatsRepo.layer,
    ),

    test("handles 'VerificationFailed' status by counting 'failed'") {
      for {
        _       <- TestClock.setTime(fixedNow)
        stats   <- ReceiptVerificationRetryJob.run
        ctx     <- ZIO.service[ReceiptVerificationTestContext]
        saved   <- ctx.savedStats.get
      } yield assertTrue(
        stats.processed == 1,
        stats.succeeded == 0,
        stats.failed == 1,
        stats.rejected == 0,
        stats.rescheduled == 0,

        saved.isDefined,
        saved.get.processed == 1
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.VerificationFailed),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockReceiptVerificationRepo.layer(List(sampleVerification)),
      MockReceiptVerificationJobStatsRepo.layer,
    ),

    test("handles 'VerificationRejected' status by counting 'rejected'") {
      for {
        _     <- TestClock.setTime(fixedNow)
        stats <- ReceiptVerificationRetryJob.run
        ctx   <- ZIO.service[ReceiptVerificationTestContext]
        saved <- ctx.savedStats.get
      } yield assertTrue(
        stats.processed == 1,
        stats.succeeded == 0,
        stats.failed == 0,
        stats.rejected == 1,
        stats.rescheduled == 0,

        saved.isDefined,
        saved.get.processed == 1
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.VerificationRejected),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockReceiptVerificationRepo.layer(List(sampleVerification)),
      MockReceiptVerificationJobStatsRepo.layer
    ),

    test("persists final stats to the repository") {
      for {
        _       <- TestClock.setTime(fixedNow)
        _       <- ReceiptVerificationRetryJob.run
        ctx     <- ZIO.service[ReceiptVerificationTestContext]
        saved   <- ctx.savedStats.get
      } yield assertTrue(
        saved.isDefined,
        saved.get.startTime == fixedNow,
        saved.get.processed == 1
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.Verified),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockReceiptVerificationRepo.layer(List(sampleVerification)),
      MockReceiptVerificationJobStatsRepo.layer
    ),

    test("Handles empty candidate list gracefully") {
      for {
        _     <- TestClock.setTime(fixedNow)
        stats <- ReceiptVerificationRetryJob.run
        ctx   <- ZIO.service[ReceiptVerificationTestContext]
        saved <- ctx.savedStats.get
      } yield assertTrue(
        stats.processed == 0,
        saved.isDefined,
        saved.get.startTime == fixedNow,
        saved.get.processed == 0 // an empty run stat was saved
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.Verified),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockReceiptVerificationRepo.layer(List.empty),
      MockReceiptVerificationJobStatsRepo.layer
    ),

    test("Fails the job if fiscal document is missing") {
      for {
        _          <- TestClock.setTime(fixedNow)
        resultExit <- ReceiptVerificationRetryJob.run.exit
      } yield assertTrue(
        resultExit.isFailure
      )
    }.provide(
      ReceiptVerificationTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.Verified),
      MockReceiptSubmissionRepository.layer(sampleSubmission.copy(fiscalDocument = None)),
      MockReceiptVerificationRepo.layer(List(sampleVerification)),
      MockReceiptVerificationJobStatsRepo.layer
    )
  )
}