package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.Ids.BonusApiSessionCode
import com.betgol.receipt.domain.clients.BonusApiSession
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.BsonDocument


object BonusApiMappers {

  extension (apiSession: BonusApiSession) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("sessionCode", apiSession.sessionCode.value.toBsonString)
        .append("updatedAt", apiSession.updatedAt.toBsonDateTime)
      doc
    }
  }

  extension (d: BsonDocument) {
    def toBonusApiSession: Either[String, BonusApiSession] =
      for {
        sessionCodeStr <- d.getStringOpt("sessionCode").toRight("Missing sessionCode")
        updatedAt <- d.getInstantOpt("updatedAt").toRight("Missing updatedAt")
      } yield BonusApiSession(BonusApiSessionCode(sessionCodeStr), updatedAt)
  }

}

