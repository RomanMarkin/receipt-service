package com.betgol.receipt.infrastructure.database

import com.betgol.receipt.config.MongoConfig
import org.mongodb.scala.{MongoClient, MongoDatabase}
import zio.{ZIO, ZLayer}


object MongoInfrastructure {

  val live: ZLayer[MongoConfig, Throwable, MongoDatabase] = ZLayer.scoped {
    for {
      c <- ZIO.service[MongoConfig]
      client <- ZIO.fromAutoCloseable(ZIO.attempt {
        val uri = s"mongodb://${c.user}:${c.pass}@${c.host}:${c.port}/${c.dbName}?authSource=${c.dbName}"
        MongoClient(uri)
      })
      _ <- ZIO.logInfo(s"Connected to MongoDB at ${c.host}")
    } yield client.getDatabase(c.dbName)
  }

}
