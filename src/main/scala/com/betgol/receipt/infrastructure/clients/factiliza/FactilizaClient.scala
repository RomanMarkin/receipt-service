package com.betgol.receipt.infrastructure.clients.factiliza

import com.betgol.receipt.config.FactilizaConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.{FiscalDocument, VerificationConfirmation}
import zio.*
import zio.http.*
import zio.json.*


case class FactilizaClient(client: Client, apiUrl: URL, apiKey: String) extends FiscalApiClient {

  override val providerName: String = "Factiliza"

  override def verify(r: FiscalDocument): IO[FiscalApiError, Option[VerificationConfirmation]] = {
    for {

      jsonPayload <- ZIO.attempt {
        FactilizaRequest.from(r).toJson
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

      apiResponse <- ZIO.fromEither(responseBody.fromJson[FactilizaResponse])
        .mapError(e => FiscalApiDeserializationError(s"[$providerName] Invalid JSON response. Body: $responseBody. Error: $e"))

      result <- validateStatus(apiResponse, r.number)

    } yield result
  }

  private def validateStatus(resp: FactilizaResponse, docNumber: String): UIO[Option[VerificationConfirmation]] = {
    if (resp.status != 200 || resp.data.isEmpty) {
      ZIO.logWarning(s"[$providerName] API returned non-200 status or no data. Status: ${resp.status}, Msg: ${resp.message.getOrElse("")}, Response: ${resp.toJsonPretty}") *>
      ZIO.none
    } else {
      val data = resp.data.get
      val description = data.descripcionCp.getOrElse("No description provided")

      // "1" = ACEPTADO
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

object FactilizaClient {
  private val HardcodedUrl = "https://api.factiliza.com/v1/sunat/cpe"

  val layer: ZLayer[Client & FactilizaConfig, Nothing, FactilizaClient] =
    ZLayer.fromZIO {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.service[FactilizaConfig]
        url <- ZIO.fromEither(URL.decode(HardcodedUrl))
          .orDieWith(e => new RuntimeException(s"FATAL: Invalid hardcoded Factiliza URL: $e"))

      } yield FactilizaClient(client, url, config.token)
    }
}