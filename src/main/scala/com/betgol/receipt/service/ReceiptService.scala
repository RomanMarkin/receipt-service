package com.betgol.receipt.service

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Types.ReceiptId
import com.betgol.receipt.domain.parsing.ReceiptParser
import com.betgol.receipt.domain.repo.{ReceiptRepository, ReceiptRetryRepository}
import zio.*


trait ReceiptService {
  def process(cmd: ProcessReceiptCommand): IO[ReceiptError, Unit]
}

case class ReceiptServiceLive(parser: ReceiptParser, repo: ReceiptRepository) extends ReceiptService {

  override def process(cmd: ProcessReceiptCommand): IO[ReceiptError, Unit] = {
    for {
      parseResult <- parser.parse(cmd.receiptData).either
      _ <- parseResult match {
        case Right(parsed) =>
          handleValidReceipt(cmd, parsed)
        case Left(errorMsg) =>
          handleInvalidReceipt(cmd, errorMsg)
      }
    } yield ()
  }

  private def handleValidReceipt(cmd: ProcessReceiptCommand, parsed: ParsedReceipt): IO[ReceiptError, ReceiptId] = {
    repo.saveValid(cmd.playerId, cmd.receiptData, parsed)
      .tapError { e => ZIO.logError(e.msg) }
  }

  private def handleInvalidReceipt(cmd: ProcessReceiptCommand, errorMsg: String): IO[ReceiptError, Unit] = {
    (ZIO.logInfo(s"Invalid receipt from ${cmd.playerId}: $errorMsg") <&>
      repo.saveInvalid(cmd.playerId, cmd.receiptData, errorMsg).ignore) *>
      ZIO.fail(InvalidReceipt(errorMsg))
  }
}

object ReceiptServiceLive {
  val layer: ZLayer[ReceiptParser & ReceiptRepository & ReceiptRetryRepository, Nothing, ReceiptService] =
    ZLayer.fromFunction(ReceiptServiceLive.apply _)
}