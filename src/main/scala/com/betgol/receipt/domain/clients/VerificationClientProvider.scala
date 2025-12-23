package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Ids.CountryCode
import zio.UIO


trait VerificationClientProvider {
  def getClientsFor(countryIso: CountryCode): UIO[List[VerificationApiClient]]
}