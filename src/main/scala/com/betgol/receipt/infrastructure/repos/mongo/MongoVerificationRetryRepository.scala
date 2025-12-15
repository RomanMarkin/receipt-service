package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.Ids.VerificationRetryId
import com.betgol.receipt.domain.repos.VerificationRetryRepository
import com.betgol.receipt.domain.{ReceiptSubmissionError, SystemError, VerificationRetry}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.VerificationRetryMappers.toBson
import org.mongodb.scala.*
import org.mongodb.scala.model.Indexes
import zio.*


case class MongoVerificationRetryRepository(db: MongoDatabase) extends VerificationRetryRepository {
  import MongoVerificationRetryRepository.CollectionName
  private val retries = db.getCollection(CollectionName)

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("submissionId")
    for {
      _ <- ZIO.fromFuture(_ => retries.createIndex(key).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(vr: VerificationRetry): IO[ReceiptSubmissionError, VerificationRetryId] =
    ZIO.fromFuture(_ => retries.insertOne(vr.toBson).toFuture())
    .as(vr.id)
    .mapError { t =>
      SystemError(s"Database failure during add: ${t.getMessage}")
    }
}

object MongoVerificationRetryRepository {
  final val CollectionName = "verification_retry"

  val layer: ZLayer[MongoDatabase, Throwable, VerificationRetryRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoVerificationRetryRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}