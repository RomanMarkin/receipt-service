package com.betgol.receipt.infrastructure.clients.factiliza

import com.betgol.receipt.config.FactilizaConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.models.FiscalDocument
import com.betgol.receipt.infrastructure.clients.HttpVerificationApiClient
import zio.*
import zio.http.*
import zio.json.*


case class FactilizaClient(client: Client, config: FactilizaConfig, apiUrl: URL) extends HttpVerificationApiClient {

  override val providerName: String = "Factiliza"

  override def verify(r: FiscalDocument): IO[VerificationApiError, VerificationResult] =
    for {
      jsonPayload <- ZIO.attempt(FactilizaRequest.from(r).toJson)
        .mapError(e => VerificationApiError.SerializationError(s"[$providerName] Request serialization failed", e))

      _ <- ZIO.logDebug(s"[$providerName] Outgoing Request: $jsonPayload")

      httpRequest = Request
        .post(apiUrl, Body.fromString(jsonPayload))
        .addHeader(Header.Authorization.Bearer(config.token))
        .addHeader(Header.ContentType(MediaType.application.json))

      responseType <- ZIO.scoped {
        client.request(httpRequest)
          .mapError(e => VerificationApiError.NetworkError(s"[$providerName] Network failure", e))
          .flatMap { response =>
            response.body.asString.map(body => (response.status.code, body))
              .mapError(e => VerificationApiError.NetworkError(s"[$providerName] Failed to read body", e))
          }
      }.timeoutFail(VerificationApiError.NetworkError(s"[$providerName] Request timed out after ${config.timeoutSeconds.seconds}", null))(config.timeoutSeconds.seconds)
      (statusCode, responseBody) = responseType

      _ <- validateHttpStatus(statusCode, responseBody)
        .tapError(e => ZIO.logError(s"[$providerName] HTTP Failure. Payload sent: $jsonPayload. Error: $e"))

      apiResponse <- ZIO.fromEither(responseBody.fromJson[FactilizaResponse])
        .mapError(e => VerificationApiError.DeserializationError(
          s"[$providerName] Invalid JSON (Status $statusCode). Body: '$responseBody'. Error: $e"
        ))

      result <- ZIO.fromEither(FactilizaClient.mapResponseToDomain(apiResponse, providerName))
    } yield result
}

object FactilizaClient {
  def mapResponseToDomain(resp: FactilizaResponse, providerName: String): Either[VerificationApiError, VerificationResult] = {
    if (resp.status != 200) {
      val msg = resp.message.getOrElse("Unknown upstream logic error")
      Left(VerificationApiError.ClientError(resp.status, s"[$providerName] API Logical Failure (Status ${resp.status}): $msg"))
    } else {
      resp.data match {
        case Some(data) =>
          data.receiptStatusCode match {
            case "0" => Right(VerificationResult(VerificationResultStatus.NotFound, description = data.receiptStatusDescription))
            case "1" => Right(VerificationResult(VerificationResultStatus.Valid, description = data.receiptStatusDescription))
            case "2" => Right(VerificationResult(VerificationResultStatus.Annulled, description = data.receiptStatusDescription))
            case unknown => Left(VerificationApiError.DeserializationError(s"[$providerName] Unknown receipt status code: '$unknown'"))
          }
        case None =>
          Left(VerificationApiError.DeserializationError(s"[$providerName] Protocol error: Status 200 but 'data' is missing."))
      }
    }
  }

  val layer: ZLayer[Client & FactilizaConfig, Nothing, FactilizaClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[FactilizaConfig]
        url <- ZIO.fromEither(URL.decode(config.url))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded Factiliza URL: $e"))

      } yield FactilizaClient(client, config, url)
    }
}