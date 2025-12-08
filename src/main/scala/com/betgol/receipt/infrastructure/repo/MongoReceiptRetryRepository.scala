package com.betgol.receipt.infrastructure.repo

import com.betgol.receipt.domain.{ReceiptError, ReceiptRetry, ReceiptRetryStatus, SystemError}
import com.betgol.receipt.domain.repo.ReceiptRetryRepository
import com.betgol.receipt.domain.Types.{CountryIsoCode, PlayerId, ReceiptId, ReceiptRetryId}
import com.betgol.receipt.infrastructure.repo.MongoMappers.toDocument
import org.mongodb.scala.*
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.bson.{Document, ObjectId}
import zio.*

import java.time.Instant
import java.util.Date


case class MongoReceiptRetryRepository(db: MongoDatabase) extends ReceiptRetryRepository {

  private val receiptRetries = db.getCollection("receipt_retry")

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("receiptId")
    ZIO.fromFuture(_ => receiptRetries.createIndex(key).toFuture()).unit
  }

  override def save(rr: ReceiptRetry): IO[ReceiptError, ReceiptRetryId] =
    for {
      data <- ZIO.attempt {
        val oid = org.bson.types.ObjectId.get()
        val doc = rr.toDocument
        doc.put("_id", oid)
        (oid, doc)
      }.mapError(e => SystemError(s"Document preparation failed: ${e.getMessage}"))
      (oid, doc) = data
      
      _ <- ZIO.fromFuture(_ => receiptRetries.insertOne(doc).toFuture())
        .mapError(t => SystemError(s"Database failure during save: ${t.getMessage}"))
    } yield ReceiptRetryId(oid.toHexString)
}

object MongoReceiptRetryRepository {
  val layer: ZLayer[MongoDatabase, Throwable, ReceiptRetryRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptRetryRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}