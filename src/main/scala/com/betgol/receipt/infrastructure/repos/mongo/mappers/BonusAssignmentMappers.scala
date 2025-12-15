package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.Ids.{BonusAssignmentId, PlayerId, SubmissionId}
import com.betgol.receipt.domain.{BonusAssignment, BonusAssignmentStatus, BonusCode}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.BsonDocument

import scala.util.Try


object BonusAssignmentMappers {

  extension (ba: BonusAssignment) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("_id",          ba.id.value.toBsonString)
        .append("submissionId", ba.submissionId.value.toBsonString)
        .append("playerId",     ba.playerId.value.toBsonString)
        .append("bonusCode",    ba.bonusCode.code.toBsonString)
        .append("status",       ba.status.toString.toBsonString)
        .append("attempt",      ba.attempt.toBsonInt32)
        .append("createdAt",    ba.createdAt.toBsonDateTime)
      ba.lastAttemptAt.foreach(inst => doc.append("lastAttemptAt", inst.toBsonDateTime))
      ba.error.foreach(err => doc.append("error", err.toBsonString))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toBonusAssignment: Either[String, BonusAssignment] = {
      for {
        id <- d.getStringOpt("_id")
          .map(BonusAssignmentId.apply)
          .toRight("Missing or invalid _id")

        submissionId <- d.getStringOpt("submissionId")
          .map(SubmissionId.apply)
          .toRight("Missing or invalid submissionId")

        playerId <- d.getStringOpt("playerId")
          .map(PlayerId.apply)
          .toRight("Missing playerId")

        bonusCode <- d.getStringOpt("bonusCode")
          .map(BonusCode.apply)
          .toRight("Missing bonusCode")

        attempt   <- d.getIntOpt("attempt").toRight("Missing attempt")
        createdAt <- d.getInstantOpt("createdAt").toRight("Missing createdAt")

        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status    <- Try(BonusAssignmentStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid bonus status: $statusStr")

        lastAttemptAt = d.getInstantOpt("lastAttemptAt")
        error         = d.getStringOpt("error")

      } yield BonusAssignment(
        id            = id,
        submissionId  = submissionId,
        playerId      = playerId,
        bonusCode     = bonusCode,
        status        = status,
        attempt       = attempt,
        createdAt     = createdAt,
        lastAttemptAt = lastAttemptAt,
        error         = error
      )
    }
  }
}