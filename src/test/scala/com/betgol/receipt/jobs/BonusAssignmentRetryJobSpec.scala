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


object BonusAssignmentRetryJobSpec extends ZIOSpecDefault {

  // Test data fixtures
  val fixedNow: Instant = Instant.parse("2026-01-01T12:00:00Z")
  val submissionId: SubmissionId = SubmissionId("sub_1")
  val assignmentId: BonusAssignmentId = BonusAssignmentId("ba_1")
  val playerId: PlayerId = PlayerId("player_1")
  val country: CountryCode = CountryCode("PE")

  val sampleBonusAssignment: BonusAssignment = BonusAssignment(
    id = assignmentId,
    submissionId = submissionId,
    playerId = playerId,
    bonusCode = BonusCode("MOCK_BONUS_CODE"),
    status = BonusAssignmentStatus.RetryScheduled,
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


  override def spec = suite("BonusAssignmentRetryJobSpec")(

    test("handles 'BonusAssigned' status by counting 'succeeded'") {
      for {
        _       <- TestClock.setTime(fixedNow)
        stats   <- BonusAssignmentRetryJob.run
        ctx     <- ZIO.service[BonusAssignmentTestContext]
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
    }.provide(
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssigned),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer,
    ),

    test("handles 'BonusAssignmentPending' status by rescheduling and counting 'rescheduled'") {
      for {
        _       <- TestClock.setTime(fixedNow)
        stats   <- BonusAssignmentRetryJob.run
        ctx     <- ZIO.service[BonusAssignmentTestContext]
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
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssignmentPending),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer,
    ),

    test("handles 'BonusAssignmentFailed' status by counting 'failed'") {
      for {
        _       <- TestClock.setTime(fixedNow)
        stats   <- BonusAssignmentRetryJob.run
        ctx     <- ZIO.service[BonusAssignmentTestContext]
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
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssignmentFailed),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer,
    ),

    test("handles 'BonusAssignmentRejected' status by counting 'rejected'") {
      for {
        _     <- TestClock.setTime(fixedNow)
        stats <- BonusAssignmentRetryJob.run
        ctx   <- ZIO.service[BonusAssignmentTestContext]
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
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssignmentRejected),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer
    ),

    test("persists final stats to the repository") {
      for {
        _       <- TestClock.setTime(fixedNow)
        _       <- BonusAssignmentRetryJob.run
        ctx     <- ZIO.service[BonusAssignmentTestContext]
        saved   <- ctx.savedStats.get
      } yield assertTrue(
        saved.isDefined,
        saved.get.startTime == fixedNow,
        saved.get.processed == 1
      )
    }.provide(
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssigned),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer
    ),

    test("Handles empty candidate list gracefully") {
      for {
        _     <- TestClock.setTime(fixedNow)
        stats <- BonusAssignmentRetryJob.run
        ctx   <- ZIO.service[BonusAssignmentTestContext]
        saved <- ctx.savedStats.get
      } yield assertTrue(
        stats.processed == 0,
        saved.isDefined,
        saved.get.startTime == fixedNow,
        saved.get.processed == 0 // an empty run stat was saved
      )
    }.provide(
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssigned),
      MockReceiptSubmissionRepository.layer(sampleSubmission),
      MockBonusAssignmentRepo.layer(List.empty),
      MockBonusAssignmentJobStatsRepo.layer
    ),

    test("Fails the job if fiscal document is missing") {
      for {
        _          <- TestClock.setTime(fixedNow)
        resultExit <- BonusAssignmentRetryJob.run.exit
      } yield assertTrue(
        resultExit.isFailure
      )
    }.provide(
      BonusAssignmentTestContext.layer,
      MockReceiptService.successPath(SubmissionStatus.BonusAssigned),
      MockReceiptSubmissionRepository.layer(sampleSubmission.copy(fiscalDocument = None)),
      MockBonusAssignmentRepo.layer(List(sampleBonusAssignment)),
      MockBonusAssignmentJobStatsRepo.layer
    )
  )
}