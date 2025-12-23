package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.VerificationId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationStatus}
import com.betgol.receipt.domain.repos.ReceiptVerificationRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.ReceiptVerificationMappers.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.Updates.*
import zio.*


case class MongoReceiptVerificationRepository(db: MongoDatabase) extends ReceiptVerificationRepository {
  import MongoReceiptVerificationRepository.CollectionName
  private val verifications = db.getCollection(CollectionName)

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("submissionId")
    for {
      _ <- ZIO.fromFuture(_ => verifications.createIndex(key).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId] =
    ZIO.fromFuture(_ => verifications.insertOne(vr.toBson).toFuture())
      .as(vr.id)
      .mapError { t => RepositoryError.InsertError(s"Database failure during adding the ReceiptVerification (id: ${vr.id.value}): ${t.getMessage}", t) }

  override def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus): IO[RepositoryError, Unit] = {
    val attemptBson = attempt.toBson

    val updateOps = combine(
      set("status", verificationStatus.toString),
      set("updatedAt", attempt.attemptedAt.toBsonDateTime),
      push("attempts", attemptBson)
    )

    ZIO.fromFuture(_ => verifications.updateOne(equal("_id", id.value), updateOps).toFuture())
      .flatMap { result =>
        if (result.getModifiedCount > 0) ZIO.unit
        else ZIO.fail(RepositoryError.NotFound(s"Cannot add attempt: ReceiptVerification not found (id: ${id.value})"))
      }
      .mapError {
        case e: RepositoryError => e
        case e => RepositoryError.UpdateError(s"Failed to add attempt to ReceiptVerification (id: ${id.value}): ${e.getMessage}", e)
      }
  }
}

object MongoReceiptVerificationRepository {
  final val CollectionName = "receipt_verification"

  val layer: ZLayer[MongoDatabase, Throwable, ReceiptVerificationRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptVerificationRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}