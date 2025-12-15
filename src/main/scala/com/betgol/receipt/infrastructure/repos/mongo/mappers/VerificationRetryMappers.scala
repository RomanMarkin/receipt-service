package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId, VerificationRetryId}
import com.betgol.receipt.domain.{VerificationRetry, VerificationRetryStatus}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.BsonDocument

import scala.util.Try


object VerificationRetryMappers {

  extension (vr: VerificationRetry) {
    def toBson: BsonDocument = new BsonDocument()
        .append("_id", vr.id.value.toBsonString)
        .append("submissionId", vr.submissionId.value.toBsonString)

        .append("playerId", vr.playerId.value.toBsonString)
        .append("country", vr.country.value.toBsonString)
        .append("status", vr.status.toString.toBsonString)

        .append("addedAt", vr.addedAt.toBsonDateTime)
        .append("attempt", vr.attempt.toBsonInt32)
  }

  extension (d: BsonDocument) {
    def toVerificationRetry: Either[String, VerificationRetry] = {
      for {
        id <- d.getStringOpt("_id")
          .map(VerificationRetryId.apply)
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

        attempt <- d.getIntOpt("attempt").toRight("Missing attempt")
        addedAt <- d.getInstantOpt("addedAt").toRight("Missing addedAt")

        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status <- Try(VerificationRetryStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid retry status: $statusStr")

      } yield VerificationRetry(
        id = id,
        submissionId = submissionId,
        playerId = playerId,
        attempt = attempt,
        addedAt = addedAt,
        country = country,
        status = status
      )
    }
  }

}
