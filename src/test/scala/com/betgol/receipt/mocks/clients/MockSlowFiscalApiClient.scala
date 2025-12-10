package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalApiError}
import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}
import zio.{IO, ZIO, durationInt}

import java.time.Instant


/** A Mock Client that returns a result after delay */
case class MockSlowFiscalApiClient(providerName: String, shouldFind: Boolean = true) extends FiscalApiClient {
  override def verify(receipt: ParsedReceipt): IO[FiscalApiError, Option[TaxAuthorityConfirmation]] = {
    if (shouldFind) {
      ZIO.sleep(1.second) *>
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
