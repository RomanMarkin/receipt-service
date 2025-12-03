package com.betgol.receipt.service

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Types.ReceiptId
import com.betgol.receipt.domain.clients.FiscalClientProvider
import com.betgol.receipt.domain.parsing.ReceiptParser
import com.betgol.receipt.domain.repo.{ReceiptRepository, ReceiptRetryRepository}
import zio.*


trait ReceiptService {
  def process(cmd: ProcessReceiptCommand): IO[ReceiptError, Unit]
}

case class ReceiptServiceLive(parser: ReceiptParser,
                              receiptRepo: ReceiptRepository,
                              receiptRetryRepo: ReceiptRetryRepository,
                              clientProvider: FiscalClientProvider) extends ReceiptService {

  private val verificationTimeout = 4.seconds

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

  private def handleValidReceipt(cmd: ProcessReceiptCommand, receipt: ParsedReceipt): IO[ReceiptError, ReceiptId] =
    for {
      receiptId <- receiptRepo.saveValid(cmd.playerId, cmd.receiptData, receipt).tapError { e => ZIO.logError(e.msg) }
      verificationResult <- verifyWithTaxAuthority(receipt)
      _ <- verificationResult match {
        case Some(confirmation) =>
          for {
            _ <- receiptRepo.updateConfirmed(receiptId, confirmation).tapError { e => ZIO.logError(e.msg) }
            _ <- ZIO.logInfo(s"Receipt verified by ${confirmation.apiProvider}")
          } yield ()

        case None =>
          for {
            _ <- ZIO.logWarning(s"Verification failed or timed out for receipt $receiptId. Queuing for retry.")
            _ <- receiptRetryRepo.save(receiptId, cmd.playerId, receipt.country).tapError { e => ZIO.logError(e.msg) }
            _ <- ZIO.fail(FiscalRecordNotFound(s"Verification pending. Added to retry queue."))
          } yield ()
      }
    } yield receiptId

  private def verifyWithTaxAuthority(receipt: ParsedReceipt): UIO[Option[TaxAuthorityConfirmation]] = {
    val clients = clientProvider.getClientsFor(receipt.country)
    if (clients.isEmpty)
      ZIO.logWarning(s"No tax authority API clients configured for country: ${receipt.country}").as(None)
    else {
      val tasks = clients.map { client =>
        client.verify(receipt)
          .flatMap {
            case Some(confirmation) => ZIO.succeed(confirmation)
            case None => ZIO.fail(s"Receipt not found in tax authority")
          }
          .catchAll { e =>
            ZIO.logWarning(s"Client ${client.providerName} failed: $e") *>
            ZIO.never //failed tasks should never stop to successfully identify the race winner
          }
      }

      val head = tasks.head
      val tail = tasks.tail
      head.raceAll(tail).timeout(verificationTimeout)
    }
  }

  private def handleInvalidReceipt(cmd: ProcessReceiptCommand, errorMsg: String): IO[ReceiptError, Unit] = {
    (ZIO.logInfo(s"Invalid receipt from ${cmd.playerId}: $errorMsg") <&>
      receiptRepo.saveInvalid(cmd.playerId, cmd.receiptData, errorMsg).ignore) *>
      ZIO.fail(InvalidReceipt(errorMsg))
  }
}

object ReceiptServiceLive {
  val layer: ZLayer[
    ReceiptParser & ReceiptRepository & ReceiptRetryRepository & FiscalClientProvider,
    Nothing,
    ReceiptService
  ] = ZLayer.fromFunction(ReceiptServiceLive.apply _)
}