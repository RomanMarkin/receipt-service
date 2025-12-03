package com.betgol.receipt.infrastructure.repo

import com.betgol.receipt.domain.{ReceiptError, ReceiptRetryStatus, SystemError}
import com.betgol.receipt.domain.repo.ReceiptRetryRepository
import com.betgol.receipt.domain.Types.{CountryIsoCode, PlayerId, ReceiptId}
import org.mongodb.scala.*
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.bson.Document
import zio.*

import java.time.Instant
import java.util.Date


case class MongoReceiptRetryRepository(db: MongoDatabase) extends ReceiptRetryRepository {

  private val receiptRetries = db.getCollection("receipt_retry")

  def ensureIndexes: Task[Unit] = {
    val key = Indexes.ascending("receiptId")
    ZIO.fromFuture(_ => receiptRetries.createIndex(key).toFuture()).unit
  }

  override def save(receiptId: ReceiptId, playerId: PlayerId, country: CountryIsoCode): IO[ReceiptError, Unit] = {
    val doc = Document(
      "receiptId" -> receiptId.toStringValue,
      "playerId"  -> playerId.toStringValue,
      "country"   -> country.toStringValue,
      "addedAt"   -> Date.from(Instant.now()),
      "attempts"  -> 0,
      "status"    -> ReceiptRetryStatus.Pending.toString
    )
    ZIO.fromFuture(_ => receiptRetries.insertOne(doc).toFuture())
      .mapError { t => SystemError(s"Database failure during save: ${t.getMessage}") }
      .unit
  }
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