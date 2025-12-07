package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Types.CountryIsoCode
import zio.UIO


trait FiscalClientProvider {
  def getClientsFor(countryIso: CountryIsoCode): UIO[List[FiscalApiClient]]
}