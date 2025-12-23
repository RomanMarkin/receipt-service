package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Ids.CountryCode
import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationClientProvider}
import zio.*


case class MockVerificationClientProvider(shouldFindDocument: Boolean) extends VerificationClientProvider {

  override def getClientsFor(countryIso: CountryCode): UIO[List[VerificationApiClient]] =
      ZIO.succeed(List(
        MockFastVerificationApiClient("MockProvider-Fast", shouldFindDocument),
        MockSlowVerificationApiClient("MockProvider-Slow", shouldFindDocument)
      )
    )
}

object MockVerificationClientProvider {
  val validDocPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(MockVerificationClientProvider(true))

  val docNotFoundPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(MockVerificationClientProvider(false))
}