package com.betgol.receipt.config

import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig


case class AppConfig(mongo: MongoConfig,
                     verificationService: VerificationServiceConfig,
                     bonusService: BonusServiceConfig)

case class MongoConfig(user: String,
                       pass: String,
                       host: String,
                       port: Int,
                       dbName: String)

case class VerificationServiceConfig(verificationTimeoutSeconds: Int = 4,
                                     maxRetries: Int = 5,
                                     clients: VerificationClientsConfig)

case class VerificationClientsConfig(apiPeru: ApiPeruConfig,
                                     factiliza: FactilizaConfig,
                                     jsonPe: JsonPeConfig)

case class ApiPeruConfig(url: String,
                         token: String,
                         timeoutSeconds: Int)

case class FactilizaConfig(url: String,
                           token: String,
                           timeoutSeconds: Int)

case class JsonPeConfig(url: String,
                        token: String,
                        timeoutSeconds: Int)

case class BonusServiceConfig(maxRetries: Int = 5,
                              bonusClient: BonusClientConfig)

case class BonusClientConfig(url: String,
                             appCode: String,
                             lang: String = "en",
                             timeoutSeconds: Int)

object AppConfig {
  val config: Config[AppConfig] = deriveConfig[AppConfig]

  val live: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO(ZIO.config(config))

  val mongo: ZLayer[Any, Config.Error, MongoConfig] =
    live.project(_.mongo)

  val verificationService: ZLayer[Any, Config.Error, VerificationServiceConfig] =
    live.project(_.verificationService)

  val bonusService: ZLayer[Any, Config.Error, BonusServiceConfig] =
    live.project(_.bonusService)

  val bettingClient: ZLayer[Any, Config.Error, BonusClientConfig] =
    live.project(_.bonusService.bonusClient)

  val apiPeruClient: ZLayer[Any, Config.Error, ApiPeruConfig] =
    live.project(_.verificationService.clients.apiPeru)

  val factilizaClient: ZLayer[Any, Config.Error, FactilizaConfig] =
    live.project(_.verificationService.clients.factiliza)

  val jsonPeClient: ZLayer[Any, Config.Error, JsonPeConfig] = {
    live.project(_.verificationService.clients.jsonPe)
  }
}