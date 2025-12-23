package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, PlayerId, SubmissionId}
import com.betgol.receipt.domain.models.{BonusAssignment, BonusAssignmentAttempt, BonusAssignmentAttemptStatus, BonusAssignmentStatus}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.{BsonArray, BsonDocument}

import scala.jdk.CollectionConverters.*
import scala.util.Try


object BonusAssignmentMappers {

  extension (attempt: BonusAssignmentAttempt) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("status", attempt.status.toString.toBsonString)
        .append("attemptNumber", attempt.attemptNumber.toBsonInt32)
        .append("attemptedAt",  attempt.attemptedAt.toBsonDateTime)
      attempt.description.foreach(e => doc.append("description", e.toBsonString))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toBonusAssignmentAttempt: Either[String, BonusAssignmentAttempt] =
      for {
        statusStr     <- d.getStringOpt("status").toRight("Missing status")
        status        <- Try(BonusAssignmentAttemptStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid status: $statusStr")
        attemptNumber <- d.getIntOpt("attemptNumber").toRight("Missing attemptNumber")
        attemptedAt   <- d.getInstantOpt("attemptedAt").toRight("Missing attemptedAt")
        description   = d.getStringOpt("description")
      } yield BonusAssignmentAttempt(status, attemptNumber, attemptedAt, description)
  }
  
  extension (ba: BonusAssignment) {
    def toBson: BsonDocument = {
      val attemptsArray = new BsonArray()
      ba.attempts.foreach(a => attemptsArray.add(a.toBson))
      new BsonDocument()
        .append("_id",          ba.id.value.toBsonString)
        .append("submissionId", ba.submissionId.value.toBsonString)
        .append("playerId",     ba.playerId.value.toBsonString)
        .append("bonusCode",    ba.bonusCode.value.toBsonString)
        .append("status",       ba.status.toString.toBsonString)
        .append("attempts",     attemptsArray)
        .append("createdAt",    ba.createdAt.toBsonDateTime)
        .append("updatedAt",    ba.updatedAt.toBsonDateTime)
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

        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status    <- Try(BonusAssignmentStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid bonus status: $statusStr")

        createdAt <- d.getInstantOpt("createdAt").toRight("Missing createdAt")
        updatedAt <- d.getInstantOpt("updatedAt").toRight("Missing updatedAt")
        
        attemptsArr <- Try(d.getArray("attempts")).toOption
          .toRight("Missing or invalid attempts array")
        
        attempts <- attemptsArr.getValues.asScala.toList
          .map(v => v.asDocument().toBonusAssignmentAttempt)
          .partitionMap(identity) match {
          case (Nil, validAttempts) => Right(validAttempts)
          case (errors, _)          => Left(s"Errors parsing attempts: ${errors.mkString(", ")}")
        }

      } yield BonusAssignment(
        id           = id,
        submissionId = submissionId,
        playerId     = playerId,
        bonusCode    = bonusCode,
        status       = status,
        attempts     = attempts,
        createdAt    = createdAt,
        updatedAt    = updatedAt
      )
    }
  }
}