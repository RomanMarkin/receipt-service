package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId, VerificationId}
import com.betgol.receipt.domain.models.{ReceiptVerification, ReceiptVerificationAttempt, ReceiptVerificationAttemptStatus, ReceiptVerificationStatus}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.{BsonArray, BsonDocument}

import scala.jdk.CollectionConverters.*
import scala.util.Try


object ReceiptVerificationMappers {

  extension (attempt: ReceiptVerificationAttempt) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("status", attempt.status.toString.toBsonString)
        .append("attemptNumber", attempt.attemptNumber.toBsonInt32)
        .append("attemptedAt", attempt.attemptedAt.toBsonDateTime)
      attempt.provider.foreach(e => doc.append("provider", e.toBsonString))
      attempt.description.foreach(e => doc.append("description", e.toBsonString))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toReceiptVerificationAttempt: Either[String, ReceiptVerificationAttempt] =
      for {
        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status <- Try(ReceiptVerificationAttemptStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid resultStatus: $statusStr")
        attemptNumber <- d.getIntOpt("attemptNumber").toRight("Missing attemptNumber")
        attemptedAt <- d.getInstantOpt("attemptedAt").toRight("Missing attemptedAt")
        provider = d.getStringOpt("provider")
        description = d.getStringOpt("description")
      } yield ReceiptVerificationAttempt(status, attemptNumber, attemptedAt, provider, description)
  }
  
  extension (v: ReceiptVerification) {
    def toBson: BsonDocument = {
      val attemptsArray = new BsonArray()
      v.attempts.foreach(a => attemptsArray.add(a.toBson))

      new BsonDocument()
        .append("_id",          v.id.value.toBsonString)
        .append("submissionId", v.submissionId.value.toBsonString)
        .append("playerId",     v.playerId.value.toBsonString)
        .append("country",      v.country.value.toBsonString)
        .append("status",       v.status.toString.toBsonString)
        .append("attempts",     attemptsArray)
        .append("createdAt",    v.createdAt.toBsonDateTime)
        .append("updatedAt",    v.updatedAt.toBsonDateTime)
    }
  }

  extension (d: BsonDocument) {
    def toReceiptVerification: Either[String, ReceiptVerification] = {
      for {
        id <- d.getStringOpt("_id")
          .map(VerificationId.apply)
          .toRight("Missing or invalid _id")

        submissionId <- d.getStringOpt("submissionId")
          .map(SubmissionId.apply)
          .toRight("Missing or invalid submissionId")

        playerId <- d.getStringOpt("playerId")
          .map(PlayerId.apply)
          .toRight("Missing playerId")

        country <- d.getStringOpt("country")
          .map(CountryCode.apply)
          .toRight("Missing country")

        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status <- Try(ReceiptVerificationStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid verification status: $statusStr")

        createdAt <- d.getInstantOpt("createdAt").toRight("Missing createdAt")
        updatedAt <- d.getInstantOpt("updatedAt").toRight("Missing updatedAt")

        attemptsArr <- Try(d.getArray("attempts")).toOption
          .toRight("Missing or invalid attempts array")

        attempts <- attemptsArr.getValues.asScala.toList
          .map(v => v.asDocument().toReceiptVerificationAttempt)
          .partitionMap(identity) match {
          case (Nil, validAttempts) => Right(validAttempts)
          case (errors, _) => Left(s"Errors parsing attempts: ${errors.mkString(", ")}")
        }

      } yield ReceiptVerification(
        id = id,
        submissionId = submissionId,
        playerId = playerId,
        country = country,
        status = status,
        attempts = attempts,
        createdAt = createdAt,
        updatedAt = updatedAt
      )
    }
    
  }

}
