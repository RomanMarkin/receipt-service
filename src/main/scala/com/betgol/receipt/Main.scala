package com.betgol.receipt

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.config.AppConfig
import com.betgol.receipt.infrastructure.clients.apiperu.ApiPeruClient
import com.betgol.receipt.infrastructure.clients.{FactilizaClient, HardcodedFiscalClientProvider}
import com.betgol.receipt.infrastructure.database.MongoInfrastructure
import com.betgol.receipt.infrastructure.parsing.SunatQrParser
import com.betgol.receipt.infrastructure.repo.{MongoReceiptRepository, MongoReceiptRetryRepository}
import com.betgol.receipt.service.ReceiptServiceLive
import org.mongodb.scala.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*


object Main extends ZIOAppDefault {

  // Setup config provider for reading application.conf
  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())


  // Compose all layers
  private val appLayer = {
    //-- DB and repos
    (AppConfig.mongo >+> MongoInfrastructure.live) >+>
    (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    //--- Tax auth api clients
    (AppConfig.apiPeru ++ Client.default) >+>
    (ApiPeruClient.layer ++ FactilizaClient.layer) >+>
    HardcodedFiscalClientProvider.layer >+>
    //--- QR parsers
    SunatQrParser.layer >+>
    //--- Services
    ReceiptServiceLive.layer ++
    Server.default
  }

  override def run: ZIO[Any, Any, Any] = {
    Server.serve(ReceiptRoutes.routes)
      .provide(appLayer)
  }
}