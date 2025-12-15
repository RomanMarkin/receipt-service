package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.config.ApiPeruConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.{FiscalDocument, VerificationConfirmation}
import zio.*
import zio.http.*
import zio.json.*


case class ApiPeruClient(client: Client, apiUrl: URL, apiKey: String) extends FiscalApiClient {

  override val providerName: String = "ApiPeru"

  override def verify(r: FiscalDocument): IO[FiscalApiError, Option[VerificationConfirmation]] = {
    for {

      jsonPayload <- ZIO.attempt {
        ApiPeruRequest.from(r).toJson
      }.mapError(e => FiscalApiSerializationError(s"[$providerName] Request serialization failed", e))

      _ <- ZIO.logDebug(s"[$providerName] Request payload: $jsonPayload")

      httpRequest = Request
        .post(apiUrl, Body.fromString(jsonPayload))
        .addHeader(Header.Authorization.Bearer(apiKey))
        .addHeader(Header.ContentType(MediaType.application.json))

      responseBody <- ZIO.scoped {
        client.request(httpRequest)
          .flatMap(_.body.asString)
          .mapError(e => FiscalApiNetworkError(s"[$providerName] Network failure", e))
      }

      apiResponse <- ZIO.fromEither(responseBody.fromJson[ApiPeruResponse])
        .mapError(e => FiscalApiDeserializationError(s"[$providerName] Invalid JSON response. Body: $responseBody. Error: $e"))

      result <- validateStatus(apiResponse, r.number)

    } yield result
  }

  private def validateStatus(resp: ApiPeruResponse, docNumber: String): UIO[Option[VerificationConfirmation]] = {
    if (!resp.success || resp.data.isEmpty) {
      ZIO.logWarning(s"[$providerName] API returned failure or no data. Msg: ${resp.message.getOrElse("")}, Response: ${resp.toJsonPretty}") *>
      ZIO.none
    } else {
      val data = resp.data.get
      val description = data.descripcionCp.getOrElse("No description provided")

      // Standard SUNAT Status:
      // "1" = Aceptado (Valid)
      // "0" = No Existe (Not Found)
      // "2" = Anulado (Annulled)
      if (data.estadoCp == "1") {
        Clock.instant.map { now =>
          Some(VerificationConfirmation(
            apiProvider = providerName,
            confirmedAt = now,
            externalId = None,
            statusMessage = description
          ))
        }
      } else {
        ZIO.logWarning(s"[$providerName] Verification failed. Status: ${data.estadoCp}. Reason: $description") *>
        ZIO.none
      }
    }
  }

}

object ApiPeruClient {
  private val HardcodedUrl = "https://apiperu.dev/api/cpe"

  val layer: ZLayer[Client & ApiPeruConfig, Nothing, ApiPeruClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[ApiPeruConfig]
        url <- ZIO.fromEither(URL.decode(HardcodedUrl))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded ApiPeru URL: $e"))

      } yield ApiPeruClient(client, url, config.token)
    }
}