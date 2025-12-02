package com.betgol.receipt

import com.betgol.receipt.config.AppConfig
import zio.*
import org.mongodb.scala.*
import com.dimafeng.testcontainers.MongoDBContainer
import org.testcontainers.utility.DockerImageName


object TestMongoLayer {

  // Get docker container, connect to test DB, return the DB
  val layer: ZLayer[Any, Throwable, MongoDatabase] = ZLayer.scoped {
    for {
      appConfig <- ZIO.config(AppConfig.config)

      dockerContainer <- ZIO.acquireRelease(ZIO.attempt {
        val c = MongoDBContainer(DockerImageName.parse("mongo:latest"))
        c.start()
        c
      })(c => ZIO.attempt(c.stop()).ignoreLogged)

      db <- ZIO.attempt {
        MongoClient(dockerContainer.replicaSetUrl)
          .getDatabase(appConfig.mongo.dbName)
      }
    } yield db
  }
}