package com.betgol.receipt

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.config.AppConfig
import com.betgol.receipt.domain.services.{BonusServiceLive, VerificationServiceLive}
import com.betgol.receipt.infrastructure.clients.HardcodedVerificationClientProvider
import com.betgol.receipt.infrastructure.clients.apiperu.ApiPeruClient
import com.betgol.receipt.infrastructure.clients.betting.BonusXmlApiClient
import com.betgol.receipt.infrastructure.clients.factiliza.FactilizaClient
import com.betgol.receipt.infrastructure.clients.jsonpe.JsonPeClient
import com.betgol.receipt.infrastructure.database.MongoInfrastructure
import com.betgol.receipt.infrastructure.parsers.SunatQrParser
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.services.ReceiptServiceLive
import org.mongodb.scala.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.logging.backend.SLF4J


object Main extends ZIOAppDefault {

  // Setup config provider for reading application.conf
  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++ // Remove ZIO's native text logger and add the SLF4J bridge
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  // Compose all layers
  private val appLayer = {
    //-- DB and repos
    (AppConfig.mongo >+> MongoInfrastructure.live) >+>
    (MongoReceiptSubmissionRepository.layer ++ MongoReceiptVerificationRepository.layer ++ MongoBonusAssignmentRepository.layer) ++
    //--- API clients
    (AppConfig.bettingClient ++ AppConfig.apiPeruClient ++ AppConfig.factilizaClient ++ AppConfig.jsonPeClient ++ Client.default) >+>
    (BonusXmlApiClient.layer ++ ApiPeruClient.layer ++ FactilizaClient.layer ++ JsonPeClient.layer) >+>
    HardcodedVerificationClientProvider.layer >+>
    //--- QR parsers
    SunatQrParser.layer >+>
    //--- Services
    (AppConfig.verificationService ++ AppConfig.bonusService) >+>
    (UuidV7IdGenerator.layer ++ HardcodedBonusEvaluator.layer) >+>
    (BonusServiceLive.layer ++ VerificationServiceLive.layer) >+>
    ReceiptServiceLive.layer ++
    Server.default
  }

  override def run: ZIO[Any, Any, Any] = {
    Server.serve(ReceiptRoutes.routes)
      .provide(appLayer)
  }
}