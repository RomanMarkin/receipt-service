package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Types.CountryIsoCode
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalClientProvider}
import zio.*


case class MockFiscalClientProvider() extends FiscalClientProvider {
  override def getClientsFor(countryIso: CountryIsoCode): List[FiscalApiClient] = {
    List(
      MockFastFiscalApiClient("MockProvider-Fast"),
      MockSlowFiscalApiClient("MockProvider-Slow")
    )
  }
}

object MockFiscalClientProvider {
  val layer: ULayer[FiscalClientProvider] =
    ZLayer.succeed(MockFiscalClientProvider())
}