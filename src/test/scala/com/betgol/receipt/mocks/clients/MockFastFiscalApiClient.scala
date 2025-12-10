package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalApiError}
import zio.{IO, ZIO}

import java.time.Instant


/** A Mock Client that always returns a result immediately */
case class MockFastFiscalApiClient(providerName: String, shouldFind: Boolean = true) extends FiscalApiClient {
  override def verify(receipt: ParsedReceipt): IO[FiscalApiError, Option[TaxAuthorityConfirmation]] = {
    if (shouldFind) {
      ZIO.succeed(Some(TaxAuthorityConfirmation(
        apiProvider = providerName,
        confirmationTime = Instant.now(),
        verificationId = s"MOCK-VERIFICATION-${receipt.docNumber}",
        statusMessage = "Mocked Success"
      )))
    } else {
      ZIO.succeed(None)
    }
  }
}
