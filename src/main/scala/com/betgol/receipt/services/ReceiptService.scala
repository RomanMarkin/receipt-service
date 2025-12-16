package com.betgol.receipt.services

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{SubmissionId, VerificationRetryId}
import com.betgol.receipt.domain.clients.{BettingApiClient, FiscalClientProvider}
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptSubmissionRepository, VerificationRetryRepository}
import com.betgol.receipt.domain.services.{BonusEvaluator, IdGenerator}
import zio.*


trait ReceiptService {
  def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult]
}

case class ReceiptServiceLive(idGenerator: IdGenerator,
                              parser: ReceiptParser,
                              receiptSubmissionRepo: ReceiptSubmissionRepository,
                              verificationRetryRepo: VerificationRetryRepository,
                              bonusAssignmentRepo: BonusAssignmentRepository,
                              clientProvider: FiscalClientProvider,
                              bonusEvaluator: BonusEvaluator,
                              bettingClient: BettingApiClient) extends ReceiptService {

  private val verificationTimeout = 4.seconds

  override def process(cmd: SubmitReceipt): IO[ReceiptSubmissionError, ReceiptSubmissionResult] = {
    for {
      parseResult <- parser.parse(cmd.receiptData).either
      result <- parseResult match {
        case Right(parsed) =>
          handleValidReceipt(cmd, parsed)
        case Left(errorMsg) =>
          handleInvalidReceipt(cmd, errorMsg)
      }
    } yield result
  }

  private def handleValidReceipt(cmd: SubmitReceipt, fiscalDocument: FiscalDocument): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    for {
      id <- idGenerator.generate.map(SubmissionId(_))
      submission = ReceiptSubmission.validSubmission(id, cmd.playerId, cmd.receiptData, fiscalDocument)
      _ <- receiptSubmissionRepo.add(submission).tapError { e => ZIO.logError(e.getMessage) }

      confirmationOpt <- verifyWithTaxAuthority(fiscalDocument) //TODO move to another service?
      submissionResult <- confirmationOpt match {
        case Some(confirmation) =>
          for {
            _ <- receiptSubmissionRepo.updateConfirmed(id, confirmation).tapError { e => ZIO.logError(e.getMessage) }
            _ <- ZIO.logInfo(s"Fiscal document was verified by [${confirmation.apiProvider}]")
          } yield ReceiptSubmissionResult(id, SubmissionStatus.ValidatedNoBonus)

        case None =>
          for {
            _ <- ZIO.logWarning(s"Verification failed or timed out for receipt $id. Queuing it for retry.")
            retryId <- idGenerator.generate.map(VerificationRetryId.apply)
            vr = VerificationRetry.initial(retryId, id, cmd.playerId, fiscalDocument.country)
            _ <- verificationRetryRepo.add(vr).tapError { e => ZIO.logError(e.getMessage) }
          } yield ReceiptSubmissionResult(id, SubmissionStatus.VerificationPending, Some("Receipt verification failed, timed out, or the receipt has not yet been approved."))
      }
    } yield submissionResult

  private[services] def verifyWithTaxAuthority(receipt: FiscalDocument): UIO[Option[VerificationConfirmation]] = {
    clientProvider.getClientsFor(receipt.country).flatMap { clients =>
      if (clients.isEmpty)
        ZIO.logWarning(s"No tax authority API clients configured for country: ${receipt.country}").as(None)
      else {
        val tasks = clients.map { client =>
          client.verify(receipt)
            .flatMap {
              case Some(confirmation) => ZIO.succeed(confirmation)
              case None => ZIO.fail(Exception(s"[${client.providerName}] Receipt not found in tax authority"))
            }
            .catchAll { e =>
              ZIO.logWarning {
                val full = Option(e.getCause)
                  .map(c => s"${e.getMessage}; cause: ${c.getMessage}")
                  .getOrElse(e.getMessage)

                s"[${client.providerName}] Failed to verify receipt: $full"} *>
              ZIO.never //failed tasks should never stop to successfully identify the race winner
            }
        }

        val head = tasks.head
        val tail = tasks.tail
        head.raceAll(tail).timeout(verificationTimeout)
      }
    }
  }

  private def handleInvalidReceipt(cmd: SubmitReceipt, errorMsg: String): IO[ReceiptSubmissionError, ReceiptSubmissionResult] =
    for {
      _ <- ZIO.logInfo(s"Invalid receipt from ${cmd.playerId}: $errorMsg")
      id <- idGenerator.generate.map(SubmissionId(_))
      submission = ReceiptSubmission.invalidSubmission(id, cmd.playerId, cmd.receiptData, errorMsg)
      _ <- receiptSubmissionRepo.add(submission).tapError { e => ZIO.logError(e.getMessage) }
    } yield ReceiptSubmissionResult(id, SubmissionStatus.InvalidReceiptData, Some(errorMsg))
}

object ReceiptServiceLive {
  val layer: ZLayer[
    IdGenerator &
      ReceiptParser &
      ReceiptSubmissionRepository &
      VerificationRetryRepository &
      BonusAssignmentRepository &
      FiscalClientProvider &
      BonusEvaluator &
      BettingApiClient,
    Nothing,
    ReceiptService
  ] = ZLayer.fromFunction(ReceiptServiceLive.apply _)
}