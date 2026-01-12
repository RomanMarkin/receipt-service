package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.ReceiptVerificationJobStats
import com.betgol.receipt.domain.repos.ReceiptVerificationJobStatsRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.VerificationJobStatsMappers.*
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.{IndexOptions, Indexes}
import zio.*

import java.util.concurrent.TimeUnit


case class MongoReceiptVerificationJobStatsRepository(db: MongoDatabase) extends ReceiptVerificationJobStatsRepository {
  import MongoReceiptVerificationJobStatsRepository.CollectionName

  private val statsCollection = db.getCollection[BsonDocument](CollectionName)

  def ensureIndexes: Task[Unit] = {
    val startTimeKey = Indexes.descending("startTime")
    val options = new IndexOptions()
      .expireAfter(365L * 24 * 60 * 60, TimeUnit.SECONDS)
      .name("startTime_ttl_idx")
    for {
      _ <- ZIO.fromFuture(_ => statsCollection.createIndex(startTimeKey, options).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(stats: ReceiptVerificationJobStats): IO[RepositoryError, Unit] =
    ZIO.fromFuture(_ => statsCollection.insertOne(stats.toBson).toFuture())
      .unit
      .mapError { t => RepositoryError.InsertError(s"Database failure during adding ReceiptVerificationJobStats (startTime: ${stats.startTime}): ${t.getMessage}", t) }
}

object MongoReceiptVerificationJobStatsRepository {
  final val CollectionName = "receipt_verification_job_stats"

  val layer: ZLayer[MongoDatabase, Throwable, ReceiptVerificationJobStatsRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptVerificationJobStatsRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}