package com.betgol.receipt.config

import zio._
import zio.config._
import zio.config.magnolia.deriveConfig


case class MongoConfig(
  user: String,
  pass: String,
  host: String,
  port: Int,
  dbName: String
)

case class AppConfig(mongo: MongoConfig)

object AppConfig {
  val config: Config[AppConfig] = deriveConfig[AppConfig]
}