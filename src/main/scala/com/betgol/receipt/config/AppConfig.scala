package com.betgol.receipt.config

import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig


case class MongoConfig(
  user: String,
  pass: String,
  host: String,
  port: Int,
  dbName: String
)

case class ApiPeruConfig(token: String)

case class FactilizaConfig(token: String)

case class JsonPeConfig(token: String)

case class AppConfig(mongo: MongoConfig,
                     apiPeru: ApiPeruConfig,
                     factiliza: FactilizaConfig,
                     jsonPe: JsonPeConfig)

object AppConfig {
  val config: Config[AppConfig] = deriveConfig[AppConfig]

  val live: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO(ZIO.config(config))

  val mongo: ZLayer[Any, Config.Error, MongoConfig] =
    live.project(_.mongo)

  val apiPeru: ZLayer[Any, Config.Error, ApiPeruConfig] =
    live.project(_.apiPeru)

  val factiliza: ZLayer[Any, Config.Error, FactilizaConfig] =
    live.project(_.factiliza)

  val jsonPe: ZLayer[Any, Config.Error, JsonPeConfig] =
    live.project(_.jsonPe)
}