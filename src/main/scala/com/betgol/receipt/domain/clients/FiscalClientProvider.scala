package com.betgol.receipt.domain.clients

import com.betgol.receipt.domain.Types.CountryIsoCode


trait FiscalClientProvider {
  def getClientsFor(countryIso: CountryIsoCode): List[FiscalApiClient]
}