package com.betgol.receipt.domain.services

import com.betgol.receipt.config.VerificationServiceConfig
import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId, VerificationId}
import com.betgol.receipt.domain.ReceiptSubmissionError
import com.betgol.receipt.domain.clients.{VerificationApiError, VerificationClientProvider, VerificationResult, VerificationResultStatus}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.ReceiptVerificationRepository
import com.betgol.receipt.domain.services.VerificationServiceLive.toVerificationStatus
import zio.*


trait VerificationService {
  def verify(submissionId: SubmissionId,
             playerId: PlayerId,
             country: CountryCode,
             receipt: FiscalDocument): IO[ReceiptSubmissionError, VerificationOutcome]

  def executeVerificationAttempt(verificationId: VerificationId,
                                 submissionId: SubmissionId,
                                 country: CountryCode,
                                 fiscalDocument: FiscalDocument,
                                 currentAttempt: Int): IO[ReceiptSubmissionError, VerificationOutcome]
}

case class VerificationServiceLive(config: VerificationServiceConfig,
                                   idGenerator: IdGenerator,
                                   repo: ReceiptVerificationRepository,
                                   clientProvider: VerificationClientProvider) extends VerificationService {

  override def verify(submissionId: SubmissionId, playerId: PlayerId, country: CountryCode, receipt: FiscalDocument): IO[ReceiptSubmissionError, VerificationOutcome] =
    for {
      id <- prepareReceiptVerification(submissionId, playerId, country, receipt)
      outcome <- executeVerificationAttempt(id, submissionId, country, receipt, currentAttempt = 1)
    } yield outcome


  override def executeVerificationAttempt(verificationId: VerificationId,
                                          submissionId: SubmissionId,
                                          country: CountryCode,
                                          fiscalDocument: FiscalDocument,
                                          currentAttempt: Int): IO[ReceiptSubmissionError, VerificationOutcome] =
    for {
      clients <- clientProvider.getClientsFor(country)

      _ <- ZIO.fail(ReceiptSubmissionError.SystemError(s"No verification providers configured for country: ${country.value}"))
        .when(clients.isEmpty)

      racingEffects = clients.map { client =>
        client.verify(fiscalDocument)
          .map(result => (client.providerName, result))
          .tapError(e => ZIO.logWarning(s"[VerificationRace] ${client.providerName} failed: ${e.getMessage}"))
      }

      raceResultEither <- ZIO.raceAll(racingEffects.head, racingEffects.tail)
        .disconnect
        .timeout(config.verificationTimeoutSeconds.seconds)
        .either

      res = VerificationServiceLive.mapRaceResult(raceResultEither)
      verificationStatus = res.status.toVerificationStatus(currentAttempt, config.maxRetries)

      now <- Clock.instant
      attempt = ReceiptVerificationAttempt(
        status = res.status,
        attemptNumber = currentAttempt,
        attemptedAt = now,
        provider = res.provider,
        description = res.description)

      _ <- repo.addAttempt(verificationId, attempt, verificationStatus)
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to save verification attempt: ${e.getMessage}"))

    } yield VerificationOutcome(
      apiProvider = res.provider,
      status = verificationStatus,
      statusDescription = res.description,
      updatedAt = now,
      externalId = res.externalId)

  private def prepareReceiptVerification(submissionId: SubmissionId, playerId: PlayerId, country: CountryCode, receipt: FiscalDocument): IO[ReceiptSubmissionError, VerificationId] =
    for {
      id <- idGenerator.generate.map(VerificationId.apply)
      now <- Clock.instant
      verification = ReceiptVerification.initial(id, submissionId, playerId, country, now)
      _ <- repo.add(verification)
        .mapError(e => ReceiptSubmissionError.SystemError(s"Failed to save ReceiptVerification (id: ${id.value}): ${e.getMessage}"))
    } yield id
}

object VerificationServiceLive {

  val layer: ZLayer[VerificationServiceConfig & IdGenerator & ReceiptVerificationRepository & VerificationClientProvider, Nothing, VerificationService] =
    ZLayer.fromFunction(VerificationServiceLive.apply _)

  private case class ClientRaceResult(provider: Option[String],
                                      status: ReceiptVerificationAttemptStatus,
                                      description: Option[String],
                                      externalId: Option[String])

  private def mapRaceResult(raceResult: Either[VerificationApiError, Option[(String, VerificationResult)]]): ClientRaceResult =
    raceResult match {
      case Right(Some((provider, result))) =>
        ClientRaceResult(
          provider = Some(provider),
          status = result.status.toVerificationAttemptStatus,
          description = result.description,
          externalId = result.externalId
        )
      case Right(None) =>
        ClientRaceResult(
          provider = None,
          status = ReceiptVerificationAttemptStatus.SystemError,
          description = Some("Verification timed out (no provider responded successfully in time)"),
          externalId = None
        )
      case Left(lastError) =>
        val msg = lastError match {
          case VerificationApiError.ServerError(code, m) => s"All providers failed. Last error: Server Error ($code) - $m"
          case VerificationApiError.ClientError(code, m) => s"All providers failed. Last error: Client Error ($code) - $m"
          case other => s"All providers failed. Last error: ${other.getMessage}"
        }
        ClientRaceResult(
          provider = None,
          status = ReceiptVerificationAttemptStatus.SystemError,
          description = Some(msg),
          externalId = None
        )
    }

  extension (resultStatus: VerificationResultStatus) {
    def toVerificationAttemptStatus: ReceiptVerificationAttemptStatus =
      resultStatus match {
        case VerificationResultStatus.Valid => ReceiptVerificationAttemptStatus.Valid
        case VerificationResultStatus.NotFound => ReceiptVerificationAttemptStatus.NotFound
        case VerificationResultStatus.Annulled => ReceiptVerificationAttemptStatus.Annulled
      }
  }

  extension (attemptStatus: ReceiptVerificationAttemptStatus) {
    def toVerificationStatus(currentAttempt: Int, maxRetries: Int): ReceiptVerificationStatus =
      attemptStatus match {
        case ReceiptVerificationAttemptStatus.Valid =>
          ReceiptVerificationStatus.Confirmed
        case ReceiptVerificationAttemptStatus.Annulled =>
          ReceiptVerificationStatus.Annulled
        case ReceiptVerificationAttemptStatus.NotFound | ReceiptVerificationAttemptStatus.SystemError =>
          if (currentAttempt < maxRetries) ReceiptVerificationStatus.RetryScheduled
          else ReceiptVerificationStatus.Exhausted
      }
  }
}