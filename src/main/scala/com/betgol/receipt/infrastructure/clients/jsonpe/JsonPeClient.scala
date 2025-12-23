package com.betgol.receipt.infrastructure.clients.jsonpe

import com.betgol.receipt.config.JsonPeConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.models.FiscalDocument
import com.betgol.receipt.infrastructure.clients.HttpVerificationApiClient
import zio.*
import zio.http.*
import zio.json.*


case class JsonPeClient(client: Client, config: JsonPeConfig, apiUrl: URL) extends HttpVerificationApiClient {

  override val providerName: String = "JsonPe"

  override def verify(r: FiscalDocument): IO[VerificationApiError, VerificationResult] = {
    for {
      jsonPayload <- ZIO.attempt(JsonPeRequest.from(r).toJson)
        .mapError(e => VerificationApiError.SerializationError(s"[$providerName] Request serialization failed", e))

      _ <- ZIO.logDebug(s"[$providerName] Outgoing Request: $jsonPayload")

      httpRequest = Request
        .post(apiUrl, Body.fromString(jsonPayload))
        .addHeader(Header.Authorization.Bearer(config.token))
        .addHeader(Header.ContentType(MediaType.application.json))

      responseTuple <- ZIO.scoped {
        client.request(httpRequest)
          .mapError(e => VerificationApiError.NetworkError(s"[$providerName] Network failure", e))
          .flatMap { response =>
            response.body.asString.map(body => (response.status.code, body))
              .mapError(e => VerificationApiError.NetworkError(s"[$providerName] Failed to read body", e))
          }
      }.timeoutFail(VerificationApiError.NetworkError(s"[$providerName] Request timed out after ${config.timeoutSeconds.seconds}", null))(config.timeoutSeconds.seconds)
      
      (statusCode, responseBody) = responseTuple
      _ <- validateHttpStatus(statusCode, responseBody)
        .tapError(e => ZIO.logError(s"[$providerName] HTTP Failure. Payload sent: $jsonPayload. Error: $e"))

      apiResponse <- ZIO.fromEither(responseBody.fromJson[JsonPeResponse])
        .mapError(e => VerificationApiError.DeserializationError(
          s"[$providerName] Invalid JSON (Status $statusCode). Body: '$responseBody'. Error: $e"
        ))

      result <- ZIO.fromEither(JsonPeClient.mapResponseToDomain(apiResponse, providerName))
    } yield result
  }
}

object JsonPeClient {
  def mapResponseToDomain(resp: JsonPeResponse, providerName: String): Either[VerificationApiError, VerificationResult] =
    if (!resp.success) {
      val msg = resp.message.getOrElse("Unknown logic error")
      Left(VerificationApiError.ClientError(200, s"[$providerName] Logical failure: $msg"))
    }
    else {
      resp.data match {
        case Some(data) =>
          data.receiptStatusCode match {
            case "0" => Right(VerificationResult(VerificationResultStatus.NotFound, description = data.receiptStatusDescription))
            case "1" => Right(VerificationResult(VerificationResultStatus.Valid, description = data.receiptStatusDescription))
            case "2" => Right(VerificationResult(VerificationResultStatus.Annulled, description = data.receiptStatusDescription))
            case other => Left(VerificationApiError.DeserializationError(s"[$providerName] Unknown receipt status code: '$other'"))
          }
        case None =>
          Left(VerificationApiError.DeserializationError(s"[$providerName] Protocol error: success=true but data is missing."))
      }
    }

  val layer: ZLayer[Client & JsonPeConfig, Nothing, JsonPeClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[JsonPeConfig]
        url <- ZIO.fromEither(URL.decode(config.url))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded JsonPe URL: $e"))

      } yield JsonPeClient(client, config, url)
    }
}