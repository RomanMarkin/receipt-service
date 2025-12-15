package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.{FiscalDocument, VerificationConfirmation}
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalApiError}
import zio.{IO, ZIO}

import java.time.Instant


/** A Mock Client that always returns a result immediately */
case class MockFastFiscalApiClient(providerName: String, shouldFind: Boolean = true) extends FiscalApiClient {
  override def verify(receipt: FiscalDocument): IO[FiscalApiError, Option[VerificationConfirmation]] = {
    if (shouldFind) {
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
