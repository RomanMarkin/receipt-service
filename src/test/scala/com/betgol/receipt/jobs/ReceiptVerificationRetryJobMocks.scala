package com.betgol.receipt.jobs

import com.betgol.receipt.domain.Ids.VerificationId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationJobStats, ReceiptVerificationStatus}
import com.betgol.receipt.domain.repos.{ReceiptVerificationJobStatsRepository, ReceiptVerificationRepository}
import zio.{IO, Ref, ZIO, ZLayer}

import java.time.Instant


case class ReceiptVerificationTestContext(savedStats: Ref[Option[ReceiptVerificationJobStats]])

object ReceiptVerificationTestContext {
  val layer: ZLayer[Any, Nothing, ReceiptVerificationTestContext] = ZLayer {
    for {
      stats <- Ref.make(Option.empty[ReceiptVerificationJobStats])
    } yield ReceiptVerificationTestContext(stats)
  }
}

class MockReceiptVerificationRepo(candidates: List[ReceiptVerification],
                                  val ctx: ReceiptVerificationTestContext) extends ReceiptVerificationRepository {
  override def findReadyForRetry(now: Instant, limit: Int): IO[RepositoryError, List[ReceiptVerification]] =
    ZIO.succeed(candidates)

  override def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId] =
    ZIO.succeed(VerificationId("mock_id")) //never used in the test

  override def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus, nextRetryAt: Option[Instant]): IO[RepositoryError, Unit] = 
    ZIO.unit
}
object MockReceiptVerificationRepo {
  def layer(candidates: List[ReceiptVerification]): ZLayer[ReceiptVerificationTestContext, Nothing, ReceiptVerificationRepository] =
    ZLayer.fromFunction((ctx: ReceiptVerificationTestContext) => new MockReceiptVerificationRepo(candidates, ctx))
}

class MockReceiptVerificationJobStatsRepo(val ctx: ReceiptVerificationTestContext) extends ReceiptVerificationJobStatsRepository {
  override def add(stats: ReceiptVerificationJobStats): IO[RepositoryError, Unit] =
    ctx.savedStats.set(Some(stats)).unit
}
object MockReceiptVerificationJobStatsRepo {
  def layer: ZLayer[ReceiptVerificationTestContext, Nothing, ReceiptVerificationJobStatsRepository] =
    ZLayer.fromFunction((ctx: ReceiptVerificationTestContext) => new MockReceiptVerificationJobStatsRepo(ctx))
}