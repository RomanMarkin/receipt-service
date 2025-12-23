package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*


trait VerificationApiClient {
  def providerName: String
  def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult]
}