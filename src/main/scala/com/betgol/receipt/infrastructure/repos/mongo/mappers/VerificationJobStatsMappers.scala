package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.models.ReceiptVerificationJobStats
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.BsonDocument


object VerificationJobStatsMappers {

  extension (stats: ReceiptVerificationJobStats) {
    def toBson: BsonDocument =
      new BsonDocument()
        .append("startTime",   stats.startTime.toBsonDateTime)
        .append("processed",   stats.processed.toBsonInt32)
        .append("succeeded",   stats.succeeded.toBsonInt32)
        .append("failed",      stats.failed.toBsonInt32)
        .append("rejected",    stats.rejected.toBsonInt32)
        .append("rescheduled", stats.rescheduled.toBsonInt32)
  }

  extension (d: BsonDocument) {
    def toVerificationJobStats: Either[String, ReceiptVerificationJobStats] =
      for {
        startTime <- d.getInstantOpt("startTime").toRight("Missing startTime")
        processed <- d.getIntOpt("processed").toRight("Missing processed count")
        succeeded <- d.getIntOpt("succeeded").toRight("Missing succeeded count")
        failed    <- d.getIntOpt("failed").toRight("Missing failed count")
        rejected  <- d.getIntOpt("rejected").toRight("Missing rejected count")
        rescheduled <- d.getIntOpt("rescheduled").toRight("Missing rescheduled count")
      } yield ReceiptVerificationJobStats(
        startTime   = startTime,
        processed   = processed,
        succeeded   = succeeded,
        failed      = failed,
        rejected    = rejected,
        rescheduled = rescheduled
      )
  }
}