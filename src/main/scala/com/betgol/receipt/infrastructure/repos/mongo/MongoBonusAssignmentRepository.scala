package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusAssignment, BonusAssignmentAttempt, BonusAssignmentStatus}
import com.betgol.receipt.domain.repos.BonusAssignmentRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.BonusAssignmentMappers.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal, lte}
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{IndexOptions, Indexes}
import zio.{IO, Task, ZIO, ZLayer}

import java.time.Instant
import java.util.concurrent.TimeUnit


case class MongoBonusAssignmentRepository(db: MongoDatabase) extends BonusAssignmentRepository {
  import MongoBonusAssignmentRepository.CollectionName
  private val bonusAssignments = db.getCollection[BsonDocument](CollectionName)

  def ensureIndexes: Task[Unit] = {
    val submissionKey = Indexes.ascending("submissionId")
    val retryKey = Indexes.ascending("status", "nextRetryAt")
    val ttlKey = Indexes.ascending("createdAt")
    val ttlOptions = new IndexOptions().expireAfter(365L * 24 * 60 * 60, TimeUnit.SECONDS) // Automatically delete documents 1 year after 'createdAt'
    for {
      _ <- ZIO.fromFuture(_ => bonusAssignments.createIndex(submissionKey).toFuture()) <&>
           ZIO.fromFuture(_ => bonusAssignments.createIndex(retryKey).toFuture()) <&>
           ZIO.fromFuture(_ => bonusAssignments.createIndex(ttlKey, ttlOptions).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(assignment: BonusAssignment): IO[RepositoryError, BonusAssignmentId] = {
    val doc = assignment.toBson
    ZIO.fromFuture(_ => bonusAssignments.insertOne(doc).toFuture())
      .as(assignment.id)
      .mapError { t => RepositoryError.InsertError(s"Database failure during adding the BonusAssignment (id: ${assignment.id.value}): ${t.getMessage}", t) }
  }

  override def addAttempt(id: BonusAssignmentId, attempt: BonusAssignmentAttempt, assignmentStatus: BonusAssignmentStatus, nextRetryAt: Option[Instant]): IO[RepositoryError, Unit] = {
    val attemptBson = attempt.toBson

    val nextRetryUpdateOp = nextRetryAt match {
      case Some(time) => set("nextRetryAt", time.toBsonDateTime)
      case None => unset("nextRetryAt")
    }

    val updateOps = combine(
      set("status", assignmentStatus.toString),
      set("updatedAt", attempt.attemptedAt.toBsonDateTime),
      nextRetryUpdateOp,
      push("attempts", attemptBson)
    )

    ZIO.fromFuture(_ => bonusAssignments.updateOne(equal("_id", id.value), updateOps).toFuture())
      .flatMap { result =>
        if (result.getMatchedCount > 0) ZIO.unit
        else ZIO.fail(RepositoryError.NotFound(s"Cannot add attempt: Bonus assignment not found (id: ${id.value})"))
      }
      .mapError {
        case e: RepositoryError => e
        case e => RepositoryError.UpdateError(s"Failed to add attempt to BonusAssignment (id: ${id.value}): ${e.getMessage}", e)
      }
  }

  override def findReadyForRetry(now: Instant, limit: Int): IO[RepositoryError, List[BonusAssignment]] = {
    val filter = and(
      equal("status", BonusAssignmentStatus.RetryScheduled.toString),
      lte("nextRetryAt", now.toBsonDateTime)
    )

    ZIO.fromFuture(_ =>
        bonusAssignments
          .find(filter)
          .sort(Indexes.ascending("nextRetryAt"))
          .limit(limit)
          .toFuture()
      )
      .mapError(t => RepositoryError.FindError(s"DB Query failed: ${t.getMessage}", t))
      .flatMap { bsonDocs =>
        val (errors, successes) = bsonDocs.map(_.toBonusAssignment).partitionMap(identity)
        if (errors.nonEmpty) {
          ZIO.logError(
            s"Skipping ${errors.size} corrupted BonusAssignment candidates. " +
              s"First failure: ${errors.headOption.getOrElse("")}"
          ).as(successes.toList)
        } else {
          ZIO.succeed(successes.toList)
        }
      }
  }
}

object MongoBonusAssignmentRepository {
  final val CollectionName = "bonus_assignments"
  
  val layer: ZLayer[MongoDatabase, Throwable, BonusAssignmentRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoBonusAssignmentRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}