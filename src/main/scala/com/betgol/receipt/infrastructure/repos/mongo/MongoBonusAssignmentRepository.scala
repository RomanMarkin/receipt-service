package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.repos.BonusAssignmentRepository
import com.betgol.receipt.domain.{BonusAssignment, BonusAssignmentError, BonusAssignmentStatus}
import org.mongodb.scala.*
import org.mongodb.scala.model.Indexes
import zio.{IO, Task, ZIO, ZLayer}


case class MongoBonusAssignmentRepository(db: MongoDatabase) extends BonusAssignmentRepository {
  import MongoBonusAssignmentRepository.CollectionName
  private val bonusAssignment = db.getCollection(CollectionName)

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("submissionId")
    for {
      _ <- ZIO.fromFuture(_ => bonusAssignment.createIndex(key).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  //TODO implement
  override def save(assignment: BonusAssignment): IO[BonusAssignmentError, Unit] = ???

  //TODO implement
  override def updateStatus(id: BonusAssignmentId, status: BonusAssignmentStatus, error: Option[String]): IO[BonusAssignmentError, Unit] = ???
}

object MongoBonusAssignmentRepository {
  final val CollectionName = "bonus_assignment"
  
  val layer: ZLayer[MongoDatabase, Throwable, BonusAssignmentRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoBonusAssignmentRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}

