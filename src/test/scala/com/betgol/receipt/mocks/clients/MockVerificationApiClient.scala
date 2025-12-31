package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationApiError, VerificationResult}
import com.betgol.receipt.domain.models.FiscalDocument
import zio.{Duration, IO, ZIO, durationInt}


case class MockVerificationApiClient(providerName: String,
                                     outcome: Either[VerificationApiError, VerificationResult],
                                     delay: Duration = 0.second) extends VerificationApiClient {

  override def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult] =
    ZIO.fromEither(outcome).delay(delay)
}
