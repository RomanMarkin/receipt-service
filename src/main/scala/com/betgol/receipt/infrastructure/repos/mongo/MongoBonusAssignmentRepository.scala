package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusAssignment, BonusAssignmentAttempt, BonusAssignmentStatus}
import com.betgol.receipt.domain.repos.BonusAssignmentRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.BonusAssignmentMappers.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.Updates.*
import zio.{IO, Task, ZIO, ZLayer}


case class MongoBonusAssignmentRepository(db: MongoDatabase) extends BonusAssignmentRepository {
  import MongoBonusAssignmentRepository.CollectionName
  private val bonusAssignments = db.getCollection(CollectionName)

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("submissionId")
    for {
      _ <- ZIO.fromFuture(_ => bonusAssignments.createIndex(key).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(assignment: BonusAssignment): IO[RepositoryError, BonusAssignmentId] = {
    val doc = assignment.toBson
    ZIO.fromFuture(_ => bonusAssignments.insertOne(doc).toFuture())
      .as(assignment.id)
      .mapError { t => RepositoryError.InsertError(s"Database failure during adding the BonusAssignment (id: ${assignment.id.value}): ${t.getMessage}", t) }
  }

  override def addAttempt(id: BonusAssignmentId, attempt: BonusAssignmentAttempt, assignmentStatus: BonusAssignmentStatus): IO[RepositoryError, Unit] = {
    val attemptBson = attempt.toBson

    val updateOps = combine(
      set("status", assignmentStatus.toString),
      set("updatedAt", attempt.attemptedAt.toBsonDateTime),
      push("attempts", attemptBson)
    )

    ZIO.fromFuture(_ => bonusAssignments.updateOne(equal("_id", id.value), updateOps).toFuture())
      .flatMap { result =>
        if (result.getModifiedCount > 0) ZIO.unit
        else ZIO.fail(RepositoryError.NotFound(s"Cannot add attempt: Bonus assignment not found (id: ${id.value})"))
      }
      .mapError {
        case e: RepositoryError => e
        case e => RepositoryError.UpdateError(s"Failed to add attempt to BonusAssignment (id: ${id.value}): ${e.getMessage}", e)
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