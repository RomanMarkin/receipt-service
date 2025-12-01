package com.betgol.receipt

import zio.*
import zio.http.*
import zio.config.typesafe.TypesafeConfigProvider
import org.mongodb.scala.*
import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.config.AppConfig
import com.betgol.receipt.domain.ReceiptParserLive
import com.betgol.receipt.repo.ReceiptRepoLive


object Main extends ZIOAppDefault {

  // Setup config provider for reading application.conf
  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  // DB connection layer
  private val mongoLayer = ZLayer.scoped {
    for {
      config <- ZIO.config(AppConfig.config)
      client <- ZIO.fromAutoCloseable(ZIO.attempt {
        val c = config.mongo
        val uri = s"mongodb://${c.user}:${c.pass}@${c.host}:${c.port}/${c.dbName}?authSource=${c.dbName}"
        MongoClient(uri)
      })
    } yield client.getDatabase(config.mongo.dbName)
  }

  // Compose all layers
  private val appLayer =
    mongoLayer >+>
      ReceiptRepoLive.layer ++
        ReceiptParserLive.layer ++
        Server.default

  override def run: ZIO[Any, Any, Any] = {
    Server.serve(ReceiptRoutes.routes)
      .provide(appLayer)
  }
}