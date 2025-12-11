package com.betgol.receipt.infrastructure.clients

import com.betgol.receipt.domain.Types.CountryIsoCode
import com.betgol.receipt.domain.clients.{FiscalApiClient, FiscalClientProvider}
import com.betgol.receipt.infrastructure.clients.apiperu.ApiPeruClient
import com.betgol.receipt.infrastructure.clients.factiliza.FactilizaClient
import com.betgol.receipt.infrastructure.clients.jsonpe.JsonPeClient
import zio.{UIO, ZIO, ZLayer}


case class HardcodedFiscalClientProvider(apiPeru: ApiPeruClient,
                                         factiliza: FactilizaClient,
                                         jsonPe: JsonPeClient) extends FiscalClientProvider {

  override def getClientsFor(countryIso: CountryIsoCode): UIO[List[FiscalApiClient]] =
    ZIO.succeed {
      countryIso.toStringValue match {
        case "PE" => List(
          //apiPeru,
          //factiliza,
          jsonPe)
        case _ => List.empty
      }
    }
}

object HardcodedFiscalClientProvider {
  val layer: ZLayer[ApiPeruClient & FactilizaClient & JsonPeClient, Nothing, FiscalClientProvider] =
    ZLayer.fromFunction(HardcodedFiscalClientProvider.apply _)
}