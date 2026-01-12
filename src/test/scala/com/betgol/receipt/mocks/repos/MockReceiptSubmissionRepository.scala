package com.betgol.receipt.mocks.repos

import com.betgol.receipt.domain.Ids.SubmissionId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusOutcome, ReceiptSubmission, SubmissionStatus, VerificationOutcome}
import com.betgol.receipt.domain.repos.ReceiptSubmissionRepository
import zio.{IO, ULayer, ZIO, ZLayer}


case class MockReceiptSubmissionRepository(submission: ReceiptSubmission) extends ReceiptSubmissionRepository {
  override def add(rs: ReceiptSubmission): IO[RepositoryError, SubmissionId] = ZIO.succeed(rs.id)
  override def getById(id: SubmissionId): IO[RepositoryError, ReceiptSubmission] = ZIO.succeed(submission)
  override def updateVerificationOutcome(submissionId: SubmissionId, status: SubmissionStatus, verification: VerificationOutcome): IO[RepositoryError, Unit] = ZIO.unit
  override def updateBonusOutcome(submissionId: SubmissionId, status: SubmissionStatus, bonus: BonusOutcome): IO[RepositoryError, Unit] = ZIO.unit
}

object MockReceiptSubmissionRepository {
  def layer(submission: ReceiptSubmission): ULayer[MockReceiptSubmissionRepository] = ZLayer.succeed(MockReceiptSubmissionRepository(submission))
}
