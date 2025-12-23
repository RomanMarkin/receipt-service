package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationApiError, VerificationResult, VerificationResultStatus}
import com.betgol.receipt.domain.models.FiscalDocument
import zio.{IO, ZIO, durationInt}


/** A Mock Client that returns a result after delay */
case class MockSlowVerificationApiClient(providerName: String, shouldFind: Boolean = true) extends VerificationApiClient {
  override def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult] = {
    val result = if (shouldFind)
      VerificationResult(VerificationResultStatus.Valid, description = Some("Mocked Valid"))
    else
      VerificationResult(VerificationResultStatus.NotFound, description = Some("Mocked Not Found"))

    ZIO.succeed(result).delay(1.second)
  }
}