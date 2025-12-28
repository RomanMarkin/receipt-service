package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.RepositoryError.{FindError, UpdateError}
import com.betgol.receipt.domain.clients.BonusApiSession
import com.betgol.receipt.domain.repos.BonusApiSessionRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.BonusApiMappers.{toBonusApiSession, toBson}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.toBsonString
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.ReplaceOptions
import zio.{IO, ZIO, ZLayer}


class MongoBonusApiSessionRepository(db: MongoDatabase) extends BonusApiSessionRepository {
  import MongoBonusApiSessionRepository.{CollectionName, SingletonId}
  private val sessions = db.getCollection[BsonDocument](CollectionName)

  override def getSession: IO[RepositoryError, Option[BonusApiSession]] =
    ZIO.fromFuture { _ =>
      sessions.find(equal("_id", SingletonId)).headOption()
    }.flatMap {
      case Some(doc) =>
        ZIO.fromEither(doc.toBonusApiSession)
          .mapError(err => new RuntimeException(s"Data corruption in session doc: $err"))
          .asSome
      case None =>
        ZIO.none
    }
    .mapError(e => FindError("Failed to retrieve bonus API session from database", e))

  override def saveSession(session: BonusApiSession): IO[RepositoryError, Unit] = {
    val docToSave = session.toBson.append("_id", SingletonId.toBsonString)
    ZIO.fromFuture { _ =>
        sessions.replaceOne(
          equal("_id", SingletonId),
          docToSave,
          ReplaceOptions().upsert(true)
        ).toFuture()
      }
      .unit
      .mapError(e => UpdateError("Failed to update bonus API session in database", e))
  }
}

object MongoBonusApiSessionRepository {
  final val CollectionName = "api_sessions"
  private final val SingletonId = "sportaq_api_session"

  val layer: ZLayer[MongoDatabase, Nothing, BonusApiSessionRepository] =
    ZLayer.fromFunction(MongoBonusApiSessionRepository(_))
}
