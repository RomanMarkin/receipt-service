package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.config.ApiPeruConfig
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}
import zio.*
import zio.http.*
import zio.json.*

import java.time.Instant


case class ApiPeruClient(client: Client, apiUrl: URL, apiKey: String) extends FiscalApiClient {

  override val providerName: String = "ApiPeru"

  override def verify(r: ParsedReceipt): IO[FiscalApiError, Option[TaxAuthorityConfirmation]] = {
    for {
      
      reqBody <- ZIO.attempt {
        Body.fromString(ApiPeruRequest.from(r).toJson)
      }.mapError(e => FiscalApiSerializationError("Failed to serialize ApiPeru request", e))

      httpRequest = Request
        .post(apiUrl, reqBody)
        .addHeader(Header.Authorization.Bearer(apiKey))
        .addHeader(Header.ContentType(MediaType.application.json))

      _ <- ZIO.logDebug(s"[$providerName] Requesting verification for Issuer Tax Id: ${r.issuerTaxId}, doc type: ${r.docType}, doc number: ${r.docSeries}-${r.docNumber}")

      responseBody <- ZIO.scoped {
        client.request(httpRequest)
          .flatMap(_.body.asString)
          .mapError(e => FiscalApiNetworkError("Failed to request ApiPeru API", e))
      }

      apiResponse <- ZIO.fromEither(responseBody.fromJson[ApiPeruResponse])
        .mapError(e => FiscalApiDeserializationError(s"Failed to parse ApiPeru JSON body: $responseBody, error: $e"))

      result <- validateStatus(apiResponse, r.docNumber)
      
    } yield result
  }

  private def validateStatus(resp: ApiPeruResponse, docNumber: String): UIO[Option[TaxAuthorityConfirmation]] = {
    if (!resp.success || resp.data.isEmpty) {
      ZIO.logWarning(s"[$providerName] API returned failure or no data: ${resp.message}") *>
      ZIO.none
    } else {
      val data = resp.data.get
      //  SUNAT standards:
      // 1: Accepted (Valid)
      // 0: Not Exists
      // 2: Annulled
      if (data.estado_cp == "1") {
        Clock.instant.map { now =>
          Some(TaxAuthorityConfirmation(
            apiProvider = providerName,
            confirmationTime = now,
            verificationId = s"API-PERU-OK-$docNumber",
            statusMessage = data.estado_cp
          ))
        }
      } else {
        ZIO.logInfo(s"[$providerName] Receipt found but state is invalid: ${data.estado_cp}") *>
        ZIO.none
      }
    }
  }

}

object ApiPeruClient {
  private val HardcodedUrl = "https://apiperu.dev/api/cpe"

  val layer: ZLayer[Client & ApiPeruConfig, Nothing, ApiPeruClient] =
    ZLayer.fromFunction { (client: Client, config: ApiPeruConfig) =>
      val url = URL.decode(HardcodedUrl)
        .getOrElse(throw new RuntimeException(s"FATAL: Invalid hardcoded API URL: $HardcodedUrl"))
      ApiPeruClient(client, url, config.token)
    }
}