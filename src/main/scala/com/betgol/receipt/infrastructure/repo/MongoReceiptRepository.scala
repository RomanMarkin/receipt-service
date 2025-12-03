package com.betgol.receipt.infrastructure.repo

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Types.*
import com.betgol.receipt.domain.repo.ReceiptRepository
import com.betgol.receipt.infrastructure.repo.MongoMappers.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model.Updates.*
import zio.*


case class MongoReceiptRepository(db: MongoDatabase) extends ReceiptRepository {

  private val receipts = db.getCollection("receipt")

  def ensureIndexes: Task[Unit] = {
    val key = compoundIndex(
      ascending("document.country"),
      ascending("document.issuerTaxId"),
      ascending("document.type"),
      ascending("document.series"),
      ascending("document.number")
    )
    val options = IndexOptions().unique(true).name("unique_receipt_idx")
    for {
      _ <- ZIO.fromFuture(_ => receipts.createIndex(key, options).toFuture())
      _ <- ZIO.logInfo("Ensured Database Indexes for receipts receipt")
    } yield ()
  }

  override def saveValid(playerId: PlayerId, rawData: String, receipt: ParsedReceipt): IO[ReceiptError, ReceiptId] = {
    val oid = org.bson.types.ObjectId.get()
    val doc = Document(
      "_id"      -> oid,
      "status"   -> ReceiptStatus.ValidReceiptData.toString,
      "request"  -> createRequestMeta(playerId, rawData),
      "document" -> receipt.toDocument
    )
    ZIO.fromFuture(_ => receipts.insertOne(doc).toFuture())
      .as(ReceiptId(oid.toHexString))
      .mapError {
        case e: MongoWriteException if e.getError.getCode == 11000 =>
          DuplicateReceipt(s"Receipt already processed: ${receipt.issuerTaxId}-${receipt.docType}-${receipt.docSeries}-${receipt.docNumber}")
        case t: Throwable =>
          SystemError(s"Database failure during saveValid: ${t.getMessage}")
      }
  }

  override def saveInvalid(playerId: PlayerId, rawData: String, errorReason: String): IO[ReceiptError, Unit] = {
    val doc = Document(
      "status"      -> ReceiptStatus.InvalidReceiptData.toString,
      "errorDetail" -> errorReason,
      "request"     -> createRequestMeta(playerId, rawData)
    )
    ZIO.fromFuture(_ => receipts.insertOne(doc).toFuture())
      .unit
      .mapError { t => SystemError(s"Database failure during saveInvalid: ${t.getMessage}") }
  }

  override def updateConfirmed(receiptId: ReceiptId, confirmation: TaxAuthorityConfirmation): IO[ReceiptError, Unit] = {
    val filter = equal("_id", new org.bson.types.ObjectId(receiptId.toStringValue))
    val update = combine(
      set("status", ReceiptStatus.Verified.toString),
      set("confirmation", confirmation.toDocument)
    )
    ZIO.fromFuture(_ => receipts.updateOne(filter, update).toFuture())
      .flatMap { res =>
        if (res.getModifiedCount == 0) ZIO.fail(new Exception(s"Receipt $receiptId not found"))
        else ZIO.unit
      }
      .mapError { t => SystemError(s"Database failure during updateConfirmed: ${t.getMessage}") }
  }
}

object MongoReceiptRepository {
  val layer: ZLayer[MongoDatabase, Throwable, ReceiptRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}