package com.betgol.receipt.infrastructure.clients

import com.betgol.receipt.domain.Types.CountryIsoCode
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalClientProvider}
import zio.{UIO, ZIO, ZLayer}


case class HardcodedFiscalClientProvider(apiPeru: ApiPeruClient,
                                         factiliza: FactilizaClient) extends FiscalClientProvider {

  override def getClientsFor(countryIso: CountryIsoCode): UIO[List[FiscalApiClient]] =
    ZIO.succeed {
      countryIso.toStringValue match {
        case "PE" => List(apiPeru, factiliza)
        case _ => List.empty
      }
    }
}

object HardcodedFiscalClientProvider {
  val layer: ZLayer[ApiPeruClient & FactilizaClient, Nothing, FiscalClientProvider] =
    ZLayer.fromFunction(HardcodedFiscalClientProvider.apply _)
}