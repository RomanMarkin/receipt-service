package com.betgol.receipt.jobs

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusAssignment, BonusAssignmentAttempt, BonusAssignmentJobStats, BonusAssignmentStatus}
import com.betgol.receipt.domain.repos.{BonusAssignmentJobStatsRepository, BonusAssignmentRepository}
import zio.{IO, Ref, ZIO, ZLayer}

import java.time.Instant


case class BonusAssignmentTestContext(savedStats: Ref[Option[BonusAssignmentJobStats]])
object BonusAssignmentTestContext {
  val layer: ZLayer[Any, Nothing, BonusAssignmentTestContext] = ZLayer {
    for {
      stats   <- Ref.make(Option.empty[BonusAssignmentJobStats])
    } yield BonusAssignmentTestContext(stats)
  }
}

class MockBonusAssignmentRepo(candidates: List[BonusAssignment],
                              val ctx: BonusAssignmentTestContext) extends BonusAssignmentRepository {
  override def findReadyForRetry(now: Instant, limit: Int): IO[RepositoryError, List[BonusAssignment]] = ZIO.succeed(candidates)
  override def add(assignment: BonusAssignment): IO[RepositoryError, BonusAssignmentId] = ZIO.succeed(BonusAssignmentId("mock_id")) //never used in the test
  override def addAttempt(id: BonusAssignmentId, attempt: BonusAssignmentAttempt, assignmentStatus: BonusAssignmentStatus, nextRetryAt: Option[Instant]): IO[RepositoryError, Unit] = ZIO.unit //never used in the test
}
object MockBonusAssignmentRepo {
  def layer(candidates: List[BonusAssignment]): ZLayer[BonusAssignmentTestContext, Nothing, BonusAssignmentRepository] =
    ZLayer.fromFunction((ctx: BonusAssignmentTestContext) => new MockBonusAssignmentRepo(candidates, ctx))
}

class MockBonusAssignmentJobStatsRepo(val ctx: BonusAssignmentTestContext) extends BonusAssignmentJobStatsRepository {
  override def add(stats: BonusAssignmentJobStats): IO[RepositoryError, Unit] =
    ctx.savedStats.set(Some(stats)).unit
}
object MockBonusAssignmentJobStatsRepo {
  def layer: ZLayer[BonusAssignmentTestContext, Nothing, BonusAssignmentJobStatsRepository] =
    ZLayer.fromFunction((ctx: BonusAssignmentTestContext) => new MockBonusAssignmentJobStatsRepo(ctx))
}