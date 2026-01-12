package com.betgol.receipt

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.config.AppConfig
import com.betgol.receipt.domain.services.{BonusServiceLive, RetryPolicyConfig, RetryPolicyLive, VerificationServiceLive}
import com.betgol.receipt.infrastructure.clients.HardcodedVerificationClientProvider
import com.betgol.receipt.infrastructure.clients.apiperu.ApiPeruClient
import com.betgol.receipt.infrastructure.clients.bonus.BonusXmlApiClient
import com.betgol.receipt.infrastructure.clients.factiliza.FactilizaClient
import com.betgol.receipt.infrastructure.clients.jsonpe.JsonPeClient
import com.betgol.receipt.infrastructure.database.MongoInfrastructure
import com.betgol.receipt.infrastructure.parsers.SunatQrParser
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusApiSessionRepository, MongoBonusAssignmentJobStatsRepository, MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationJobStatsRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.jobs.{BonusAssignmentRetryJob, ReceiptVerificationRetryJob}
import com.betgol.receipt.services.ReceiptServiceLive
import org.mongodb.scala.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.logging.backend.SLF4J


object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++ // Remove ZIO's native text logger and add the SLF4J bridge
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) // Setup config provider for reading application.conf

  private val jobSchedule = Schedule.fixed(30.seconds)

  override def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZIO.logInfo("Bootstrapping Receipt Service Application...")

      appRole <- System.env("APP_ROLE").someOrElse("server")
      _ <- ZIO.logInfo(s"Starting with role: [$appRole]")

      _ <- appRole match {
        case "server" =>
          Server.serve(ReceiptRoutes.routes)
            .provide(AppLayers.serverLayer)

        case "receipt_verification_retry_job" =>
          ReceiptVerificationRetryJob.run
            .repeat(jobSchedule)
            .provide(AppLayers.receiptVerificationJobLayer)

        case "bonus_assignment_retry_job" =>
          BonusAssignmentRetryJob.run
            .repeat(jobSchedule)
            .provide(AppLayers.bonusAssignmentJobLayer)

        case unknown =>
          ZIO.fail(new RuntimeException(s"Unknown APP_ROLE: $unknown. Valid values are: server, receipt_verification_retry_job, bonus_assignment_retry_job"))
      }
    } yield ()
}

object AppLayers {
  private val baseLayer =
    //-- DB and repos
    (AppConfig.mongo >+> MongoInfrastructure.live) >+>
    (MongoReceiptSubmissionRepository.layer ++ MongoReceiptVerificationRepository.layer ++ MongoBonusAssignmentRepository.layer ++ MongoBonusApiSessionRepository.layer) ++
    //--- API clients
    (AppConfig.bettingClient ++ AppConfig.apiPeruClient ++ AppConfig.factilizaClient ++ AppConfig.jsonPeClient ++ Client.default) >+>
    (BonusXmlApiClient.layer ++ ApiPeruClient.layer ++ FactilizaClient.layer ++ JsonPeClient.layer) >+>
    HardcodedVerificationClientProvider.layer >+>
    //--- QR parsers
    SunatQrParser.layer >+>
    //--- Services
    (RetryPolicyConfig.layer >>> RetryPolicyLive.layer) >+>
    (AppConfig.verificationService ++ AppConfig.bonusService) >+>
    (UuidV7IdGenerator.layer ++ HardcodedBonusEvaluator.layer) >+>
    (BonusServiceLive.layer ++ VerificationServiceLive.layer) >+>
    ReceiptServiceLive.layer

  val serverLayer =
    baseLayer >+>
    Server.default

  val receiptVerificationJobLayer =
    baseLayer >+>
    MongoReceiptVerificationJobStatsRepository.layer

  val bonusAssignmentJobLayer =
    baseLayer >+>
    MongoBonusAssignmentJobStatsRepository.layer
}