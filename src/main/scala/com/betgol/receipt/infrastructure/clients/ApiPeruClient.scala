package com.betgol.receipt.infrastructure.clients

import zio._
import com.betgol.receipt.domain.clients.FiscalApiClient
import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}
import java.time.Instant


case class ApiPeruClient() extends FiscalApiClient {

  override val providerName: String = "ApiPeru"

  override def verify(receipt: ParsedReceipt): IO[Throwable, Option[TaxAuthorityConfirmation]] = {

    // TODO Implement real logic instead of this simulation (fast client)
    ZIO.sleep(200.millis) *>
      ZIO.logInfo(s"[$providerName] Checking receipt ${receipt.docNumber}...") *>
      ZIO.succeed {
        Some(TaxAuthorityConfirmation(
          apiProvider = providerName,
          confirmationTime = Instant.now(),
          verificationId = s"API-PERU-HASH-${receipt.docNumber}",
          statusMessage = "Aceptado"
        ))
      }
  }
}

object ApiPeruClient {
  val layer: ULayer[ApiPeruClient] = ZLayer.succeed(ApiPeruClient())
}