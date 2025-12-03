package com.betgol.receipt

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.config.AppConfig
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
    mongoLayer >+> (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    SunatQrParser.layer >+> ReceiptServiceLive.layer ++
    Server.default

  override def run: ZIO[Any, Any, Any] = {
    Server.serve(ReceiptRoutes.routes)
      .provide(appLayer)
  }
}