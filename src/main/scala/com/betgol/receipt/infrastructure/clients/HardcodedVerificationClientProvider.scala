package com.betgol.receipt.infrastructure.clients

import com.betgol.receipt.domain.Ids.CountryCode
import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationClientProvider}
import com.betgol.receipt.infrastructure.clients.apiperu.ApiPeruClient
import com.betgol.receipt.infrastructure.clients.factiliza.FactilizaClient
import com.betgol.receipt.infrastructure.clients.jsonpe.JsonPeClient
import zio.{UIO, ZIO, ZLayer}


case class HardcodedVerificationClientProvider(apiPeru: ApiPeruClient,
                                               factiliza: FactilizaClient,
                                               jsonPe: JsonPeClient) extends VerificationClientProvider {

  override def getClientsFor(countryCode: CountryCode): UIO[List[VerificationApiClient]] =
    ZIO.succeed {
      countryCode.value match {
        case "PE" => List(
          apiPeru,
          factiliza,
          jsonPe)
        case _ => List.empty
      }
    }
}

object HardcodedVerificationClientProvider {
  val layer: ZLayer[ApiPeruClient & FactilizaClient & JsonPeClient, Nothing, VerificationClientProvider] =
    ZLayer.fromFunction(HardcodedVerificationClientProvider.apply _)
}