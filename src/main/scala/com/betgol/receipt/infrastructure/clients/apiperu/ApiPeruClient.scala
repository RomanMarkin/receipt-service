package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.config.ApiPeruConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.models.FiscalDocument
import com.betgol.receipt.infrastructure.clients.HttpVerificationApiClient
import zio.*
import zio.http.*
import zio.json.*


case class ApiPeruClient(client: Client, config: ApiPeruConfig, apiUrl: URL) extends HttpVerificationApiClient {

  override val providerName: String = "ApiPeru"

  override def verify(r: FiscalDocument): IO[VerificationApiError, VerificationResult] =
    for {
      jsonPayload <- ZIO.attempt(ApiPeruRequest.from(r).toJson)
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

      apiResponse <- ZIO.fromEither(responseBody.fromJson[ApiPeruResponse])
        .mapError(e => VerificationApiError.DeserializationError(
          s"[$providerName] Invalid JSON (Status $statusCode). Body: '$responseBody'. Error: $e"
        ))

      result <- ZIO.fromEither(ApiPeruClient.mapResponseToDomain(apiResponse, providerName))
    } yield result
}

object ApiPeruClient {
  def mapResponseToDomain(resp: ApiPeruResponse, providerName: String): Either[VerificationApiError, VerificationResult] = {
    (resp.success, resp.data) match {
      case (true, Some(data)) =>
        data.receiptStatusCode match {
          case "0" => Right(VerificationResult(VerificationResultStatus.NotFound, description = data.receiptStatusDescription))
          case "1" => Right(VerificationResult(VerificationResultStatus.Valid, description = data.receiptStatusDescription))
          case "2" => Right(VerificationResult(VerificationResultStatus.Annulled, description = data.receiptStatusDescription))
          case unknown => Left(VerificationApiError.DeserializationError(s"[$providerName] Unknown receipt status code: '$unknown'"))
        }

      case (true, None) =>
        Left(VerificationApiError.DeserializationError(s"[$providerName] Protocol error: Success=true but data is missing."))

      case (false, _) =>
        val msg = resp.message.getOrElse("Unknown logic error")
        Left(VerificationApiError.ClientError(200, s"[$providerName] Logical failure: $msg"))
    }
  }

  val layer: ZLayer[Client & ApiPeruConfig, Nothing, ApiPeruClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[ApiPeruConfig]
        url <- ZIO.fromEither(URL.decode(config.url))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid ApiPeru URL: $e"))

      } yield ApiPeruClient(client, config, url)
    }
}