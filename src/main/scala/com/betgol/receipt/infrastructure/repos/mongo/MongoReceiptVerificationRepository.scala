package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.VerificationId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationStatus}
import com.betgol.receipt.domain.repos.ReceiptVerificationRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.ReceiptVerificationMappers.*
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal, lte}
import org.mongodb.scala.model.{IndexOptions, Indexes}
import org.mongodb.scala.model.Updates.*
import zio.*

import java.time.Instant
import java.util.concurrent.TimeUnit


case class MongoReceiptVerificationRepository(db: MongoDatabase) extends ReceiptVerificationRepository {
  import MongoReceiptVerificationRepository.CollectionName
  private val verifications = db.getCollection[BsonDocument](CollectionName)

  def ensureIndexes: Task[Unit] = {
    val submissionKey = Indexes.ascending("submissionId")
    val retryKey = Indexes.ascending("status", "nextRetryAt")
    val ttlKey = Indexes.ascending("createdAt")
    val ttlOptions = new IndexOptions().expireAfter(365L * 24 * 60 * 60, TimeUnit.SECONDS) // Automatically delete documents 1 year after 'createdAt'
    for {
      _ <- ZIO.fromFuture(_ => verifications.createIndex(submissionKey).toFuture()) <&>
           ZIO.fromFuture(_ => verifications.createIndex(retryKey).toFuture()) <&>
           ZIO.fromFuture(_ => verifications.createIndex(ttlKey, ttlOptions).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId] =
    ZIO.fromFuture(_ => verifications.insertOne(vr.toBson).toFuture())
      .as(vr.id)
      .mapError { t => RepositoryError.InsertError(s"Database failure during adding the ReceiptVerification (id: ${vr.id.value}): ${t.getMessage}", t) }

  override def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus, nextRetryAt: Option[Instant]): IO[RepositoryError, Unit] = {
    val attemptBson = attempt.toBson

    val nextRetryUpdateOp = nextRetryAt match {
      case Some(time) => set("nextRetryAt", time.toBsonDateTime)
      case None => unset("nextRetryAt")
    }

    val updateOps = combine(
      set("status", verificationStatus.toString),
      set("updatedAt", attempt.attemptedAt.toBsonDateTime),
      nextRetryUpdateOp,
      push("attempts", attemptBson)
    )

    ZIO.fromFuture(_ => verifications.updateOne(equal("_id", id.value), updateOps).toFuture())
      .flatMap { result =>
        if (result.getMatchedCount > 0) ZIO.unit
        else ZIO.fail(RepositoryError.NotFound(s"Cannot add attempt: ReceiptVerification not found (id: ${id.value})"))
      }
      .mapError {
        case e: RepositoryError => e
        case e => RepositoryError.UpdateError(s"Failed to add attempt to ReceiptVerification (id: ${id.value}): ${e.getMessage}", e)
      }
  }

  override def findReadyForRetry(now: Instant, limit: Int): IO[RepositoryError, List[ReceiptVerification]] = {
    val filter = and(
      equal("status", ReceiptVerificationStatus.RetryScheduled.toString),
      lte("nextRetryAt", now.toBsonDateTime)
    )

    ZIO.fromFuture(_ =>
        verifications
          .find(filter)
          .sort(Indexes.ascending("nextRetryAt"))
          .limit(limit)
          .toFuture()
      )
      .mapError(t => RepositoryError.FindError(s"DB Query failed: ${t.getMessage}", t))
      .flatMap { bsonDocs =>
        val (errors, successes) = bsonDocs.map(_.toReceiptVerification).partitionMap(identity)
        if (errors.nonEmpty) {
          ZIO.logError(
            s"Skipping ${errors.size} corrupted ReceiptVerification candidates. " +
            s"First failure: ${errors.headOption.getOrElse("")}"
          ).as(successes.toList)
        } else {
          ZIO.succeed(successes.toList)
        }
      }
  }
}

object MongoReceiptVerificationRepository {
  final val CollectionName = "receipt_verifications"

  val layer: ZLayer[MongoDatabase, Throwable, ReceiptVerificationRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptVerificationRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}