package com.betgol.receipt.repo

import com.betgol.receipt.domain.*
import org.mongodb.scala.*
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.MongoWriteException
import zio.*

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date


trait ReceiptRepo {
  def saveValid(playerId: String, rawData: String, parsed: ParsedReceipt): Task[Unit]
  def saveInvalid(playerId: String, rawData: String, errorReason: String): Task[Unit]
  def ensureIndexes: Task[Unit]
}

case class ReceiptRepoLive(db: MongoDatabase) extends ReceiptRepo {

  private val collection = db.getCollection("receipt")

  override def saveValid(playerId: String, rawData: String, parsed: ParsedReceipt): Task[Unit] = {
    val doc = Document(
      "status"        -> ReceiptStatus.ValidReceiptData.toString,
      "request"       -> request(playerId, rawData),
      "document"      -> fiscalDocument(parsed)
    )
    ZIO.fromFuture(_ => collection.insertOne(doc).toFuture()).unit
  }

  override def saveInvalid(playerId: String, rawData: String, errorReason: String): Task[Unit] = {
    val doc = Document(
      "status"        -> ReceiptStatus.InvalidReceiptData.toString,
      "errorDetail"   -> errorReason,
      "request"       -> request(playerId, rawData)
    )
    ZIO.fromFuture(_ => collection.insertOne(doc).toFuture()).unit
  }

  override def ensureIndexes: Task[Unit] = {
    val keys = compoundIndex(
      ascending("document.issuerTaxId"),
      ascending("document.type"),
      ascending("document.series"),
      ascending("document.number")
    )

    val indexOptions = IndexOptions()
      .unique(true)
      .name("unique_receipt_idx")

    ZIO.fromFuture(_ => collection.createIndex(keys, indexOptions).toFuture())
      .unit
      .tap(_ => ZIO.logInfo("Ensured Database Indexes"))
  }

  private def request(playerId: String, rawData: String) = Document(
    "requestDate" -> Date.from(Instant.now()),
    "playerId"      -> playerId,
    "rawData"       -> rawData
  )

  private def fiscalDocument(r: ParsedReceipt) = Document(
    "issuerTaxId" -> r.issuerTaxId,
    "type" -> r.docType,
    "series" -> r.docSeries,
    "number" -> r.docNumber,
    "date" -> r.date.toDate,
    "totalAmount" -> r.totalAmount
  )

  implicit class LocalDateOpt(d: LocalDate) {
    def toDate: Date = Date.from(d.atStartOfDay(ZoneId.of("UTC")).toInstant)
  }
}

object ReceiptRepoLive {
  val layer: ZLayer[MongoDatabase, Throwable, ReceiptRepo] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = ReceiptRepoLive(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}