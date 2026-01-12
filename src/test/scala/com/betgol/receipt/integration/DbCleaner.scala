package com.betgol.receipt.integration

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{MongoDatabase, ObservableFuture}
import zio.ZIO


object DbCleaner {

  /**
   * Truncates all collections in the database (except system collections).
   * Safe to run before/after tests to ensure a clean slate.
   */
  val clean: ZIO[MongoDatabase, Throwable, Unit] =
    for {
      db <- ZIO.service[MongoDatabase]
      collectionNames <- ZIO.fromFuture(_ => db.listCollectionNames().toFuture())

      targetCollections = collectionNames.filterNot(_.startsWith("system."))

      _ <- ZIO.foreachPar(targetCollections) { name =>
        ZIO.fromFuture(_ => db.getCollection[BsonDocument](name).deleteMany(BsonDocument()).toFuture())
      }

      _ <- ZIO.logInfo("Database collections truncated.")
    } yield ()
}