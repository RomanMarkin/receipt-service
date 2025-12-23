package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationApiError, VerificationResult, VerificationResultStatus}
import com.betgol.receipt.domain.models.FiscalDocument
import zio.{IO, ZIO}


/** A Mock Client that always returns a result immediately */
case class MockFastVerificationApiClient(providerName: String, shouldFind: Boolean = true) extends VerificationApiClient {
  override def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult] = {
    val result = if (shouldFind)
      VerificationResult(VerificationResultStatus.Valid, description = Some("Mock Valid"), externalId = Some("mocked-external-id"))
    else
      VerificationResult(VerificationResultStatus.NotFound, description = Some("Mock Not Found"), externalId = None)

    ZIO.succeed(result)
  }
}
