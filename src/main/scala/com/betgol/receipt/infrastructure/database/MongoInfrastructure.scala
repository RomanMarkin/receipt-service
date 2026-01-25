package com.betgol.receipt.infrastructure.database

import com.betgol.receipt.config.MongoConfig
import org.mongodb.scala.{MongoClient, MongoDatabase}
import zio.{ZIO, ZLayer}


object MongoInfrastructure {

  val live: ZLayer[MongoConfig, Throwable, MongoDatabase] = ZLayer.scoped {
    for {
      c <- ZIO.service[MongoConfig]

      uri <- ZIO.attempt {
        c.uri match {
          // High priority
          case Some(connectionString) if connectionString.nonEmpty =>
            connectionString
          // Low priority
          case _ =>
            (c.user, c.pass, c.host, c.port) match {
              case (Some(u), Some(p), Some(h), Some(port)) =>
                s"mongodb://$u:$p@$h:$port/${c.dbName}?authSource=${c.dbName}"
              case _ =>
                throw new RuntimeException(
                  "Invalid MongoDB Config: Either 'uri' OR ('host', 'port', 'user', 'pass') must be defined."
                )
            }
        }
      }

      client <- ZIO.fromAutoCloseable(ZIO.attempt(MongoClient(uri)))
      _      <- ZIO.logInfo(s"Connected to MongoDB (Database: ${c.dbName})")

    } yield client.getDatabase(c.dbName)
  }
}