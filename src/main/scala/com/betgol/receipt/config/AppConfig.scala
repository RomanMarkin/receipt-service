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

case class AppConfig(mongo: MongoConfig, apiPeru: ApiPeruConfig)

object AppConfig {
  val config: Config[AppConfig] = deriveConfig[AppConfig]

  val live: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO(ZIO.config(config))

  val mongo: ZLayer[Any, Config.Error, MongoConfig] =
    live.project(_.mongo)

  val apiPeru: ZLayer[Any, Config.Error, ApiPeruConfig] =
    live.project(_.apiPeru)
}