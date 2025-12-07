package com.betgol.receipt.fixtures

import com.betgol.receipt.config.AppConfig
import com.dimafeng.testcontainers.MongoDBContainer
import org.mongodb.scala.*
import org.testcontainers.utility.DockerImageName
import zio.*


object TestMongoLayer {

  val layer: ZLayer[Any, Throwable, MongoDatabase] = ZLayer.scoped {
    for {
      appConfig <- ZIO.config(AppConfig.config)
      dockerContainer <- ZIO.acquireRelease(ZIO.attemptBlocking {
        val c = MongoDBContainer(DockerImageName.parse("mongo:latest"))
        c.start()
        ZIO.logInfo(s"Container started: ${c.replicaSetUrl}").unsafeRun
        c
      })(c =>
        (ZIO.logInfo(s"Stopping MongoDB container...") *>
          ZIO.attemptBlocking(c.stop()) *>
          ZIO.logInfo("MongoDB container stopped."))
          .catchAll(e => ZIO.logError(s"Failed to stop container: $e"))
      )

      db <- ZIO.attempt {
        MongoClient(dockerContainer.replicaSetUrl)
          .getDatabase(appConfig.mongo.dbName)
      }
    } yield db
  }

  // Helper to bridge blocking log inside the acquire block if needed,
  // or just rely on the fact that acquireRelease failure will show up.
  implicit class UnsafeRunOps[A](io: UIO[A]) {
    def unsafeRun: A = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(io).getOrThrow()
    }
  }
}