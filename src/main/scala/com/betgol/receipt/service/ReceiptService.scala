package com.betgol.receipt.service

import zio._
import com.betgol.receipt.domain._
import com.betgol.receipt.repo.ReceiptRepo
import org.mongodb.scala.MongoWriteException


trait ReceiptService {
  def process(req: ReceiptRequest): IO[ReceiptError, Unit]
}

case class ReceiptServiceLive(parser: ReceiptParser, repo: ReceiptRepo) extends ReceiptService {

  override def process(req: ReceiptRequest): IO[ReceiptError, Unit] = {
    for {
      parseResult <- parser.parse(req.receiptData).either
      _ <- parseResult match {
        case Right(parsed) =>
          handleValidReceipt(req, parsed)
        case Left(errorMsg) =>
          handleInvalidReceipt(req, errorMsg)
      }
    } yield ()
  }

  private def handleValidReceipt(req: ReceiptRequest, parsed: ParsedReceipt): IO[ReceiptError, Unit] = {
    repo.saveValid(req.playerId, req.receiptData, parsed)
      .mapError {
        case e: MongoWriteException if e.getError.getCode == 11000 =>
          DuplicateReceipt(s"Receipt already processed: issuerTaxId = ${parsed.issuerTaxId}, docType = ${parsed.docType}, docSeries = ${parsed.docSeries}, docNumber = ${parsed.docNumber}")
        case e =>
          SystemError(s"Database failure: ${e.getMessage}")
      }
      .tapError {
        case d: DuplicateReceipt => ZIO.logWarning(s"Duplicate: ${d.msg}")
        case s: SystemError => ZIO.logError(s.msg)
        case _ => ZIO.unit
      }
  }

  private def handleInvalidReceipt(req: ReceiptRequest, errorMsg: String): IO[ReceiptError, Unit] = {
    (ZIO.logInfo(s"Invalid receipt from ${req.playerId}: $errorMsg") <&>
      repo.saveInvalid(req.playerId, req.receiptData, errorMsg).ignore) *>
      ZIO.fail(InvalidReceipt(errorMsg))
  }
}

object ReceiptServiceLive {
  val layer: ZLayer[ReceiptParser & ReceiptRepo, Nothing, ReceiptService] =
    ZLayer.fromFunction(ReceiptServiceLive.apply _)
}