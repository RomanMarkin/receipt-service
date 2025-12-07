package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Types.CountryIsoCode
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalClientProvider}
import zio.*


case class MockFiscalClientProvider(shouldFindDocument: Boolean) extends FiscalClientProvider {

  override def getClientsFor(countryIso: CountryIsoCode): UIO[List[FiscalApiClient]] =
      ZIO.succeed(List(
        MockFastFiscalApiClient("MockProvider-Fast", shouldFindDocument),
        MockSlowFiscalApiClient("MockProvider-Slow", shouldFindDocument)
      )
    )
}

object MockFiscalClientProvider {
  val happyPath: ULayer[FiscalClientProvider] =
    ZLayer.succeed(MockFiscalClientProvider(true))

  val docNotFoundPath: ULayer[FiscalClientProvider] =
    ZLayer.succeed(MockFiscalClientProvider(false))
}