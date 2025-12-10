package com.betgol.receipt.infrastructure.clients

import zio.*
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalApiError}
import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}

import java.time.Instant


case class FactilizaClient() extends FiscalApiClient {

  override val providerName: String = "Factiliza"

  override def verify(receipt: ParsedReceipt): IO[FiscalApiError, Option[TaxAuthorityConfirmation]] = {

    // TODO Implement real logic instead of this simulation (slow client)
    ZIO.sleep(100.second) *>
      ZIO.logInfo(s"[$providerName] Checking receipt ${receipt.docNumber}...") *>
      ZIO.succeed {
        Some(TaxAuthorityConfirmation(
          apiProvider = providerName,
          confirmationTime = Instant.now(),
          verificationId = s"FACTILIZA-ID-${receipt.docNumber}",
          statusMessage = "Comprobante existe y es válido"
        ))
      }
  }
}

object FactilizaClient {
  val layer: ULayer[FactilizaClient] = ZLayer.succeed(FactilizaClient())
}