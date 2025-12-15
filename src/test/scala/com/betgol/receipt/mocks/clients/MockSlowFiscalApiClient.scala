package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalApiError}
import com.betgol.receipt.domain.{FiscalDocument, VerificationConfirmation}
import zio.{IO, ZIO, durationInt}

import java.time.Instant


/** A Mock Client that returns a result after delay */
case class MockSlowFiscalApiClient(providerName: String, shouldFind: Boolean = true) extends FiscalApiClient {
  override def verify(receipt: FiscalDocument): IO[FiscalApiError, Option[VerificationConfirmation]] = {
    if (shouldFind) {
      ZIO.sleep(1.second) *>
      ZIO.succeed(Some(VerificationConfirmation(
        apiProvider = providerName,
        confirmedAt = Instant.now(),
        externalId = None,
        statusMessage = "Mocked Success"
      )))
    } else {
      ZIO.succeed(None)
    }
  }
}
