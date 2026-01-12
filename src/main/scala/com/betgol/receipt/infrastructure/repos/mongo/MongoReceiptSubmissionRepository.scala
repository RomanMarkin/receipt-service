package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.models.{BonusOutcome, ReceiptSubmission, SubmissionStatus, VerificationOutcome}
import com.betgol.receipt.domain.repos.ReceiptSubmissionRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.ReceiptSubmissionMappers.{toBson, toReceiptSubmission}
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{Filters, IndexOptions}
import zio.*


case class MongoReceiptSubmissionRepository(db: MongoDatabase) extends ReceiptSubmissionRepository {
  import MongoReceiptSubmissionRepository.CollectionName
  private val receiptSubmissions = db.getCollection[BsonDocument](CollectionName)

  def ensureIndexes: Task[Unit] = {
    val fiscalDocumentKey = compoundIndex(
      ascending("fiscalDocument.country"),
      ascending("fiscalDocument.issuerTaxId"),
      ascending("fiscalDocument.type"),
      ascending("fiscalDocument.series"),
      ascending("fiscalDocument.number")
    )
    val fiscalDocumentOptions = IndexOptions()
      .unique(true)
      .name("unique_receipt_idx")
      .partialFilterExpression(Filters.exists("fiscalDocument"))

    val playerHistoryKey = compoundIndex(
      ascending("metadata.playerId"),
      ascending("metadata.submittedAt")
    )
    val playerHistoryOptions = IndexOptions().name("player_history_idx")
    for {
      _ <- ZIO.fromFuture(_ => receiptSubmissions.createIndex(fiscalDocumentKey, fiscalDocumentOptions).toFuture()) <&>
           ZIO.fromFuture(_ => receiptSubmissions.createIndex(playerHistoryKey, playerHistoryOptions).toFuture())
      _ <- ZIO.logInfo(s"Ensured database indexes for collection [$CollectionName]")
    } yield ()
  }

  override def add(rs: ReceiptSubmission): IO[RepositoryError, SubmissionId] = {
    ZIO.fromFuture(_ => receiptSubmissions.insertOne(rs.toBson).toFuture())
      .as(rs.id)
      .mapError {
        case e: MongoWriteException if e.getError.getCode == 11000 =>
          val details = rs.fiscalDocument match {
            case Some(fd) => s"[Issuer: ${fd.issuerTaxId}, DocNum: ${fd.series}-${fd.number}]"
            case None     => s"[SubmissionId: ${rs.id.value}]"
          }
          RepositoryError.Duplicate(s"Receipt already exists: $details", e)
        case t: Throwable =>
          RepositoryError.InsertError(s"Failed to insert ReceiptSubmission ${rs.id.value}. Cause: ${t.getMessage}", t)
      }
  }

  override def getById(id: SubmissionId): IO[RepositoryError, ReceiptSubmission] =
    ZIO.fromFuture(_ =>
        receiptSubmissions
          .find(equal("_id", id.value))
          .first()
          .toFutureOption()
      )
      .mapError(t => RepositoryError.FindError(s"Database error fetching ReceiptSubmission ${id.value}: ${t.getMessage}", t))
      .flatMap {
        case Some(doc) =>
          ZIO.fromEither(doc.toReceiptSubmission)
            .mapError(e => RepositoryError.DataCorrupted(s"Failed to parse ReceiptSubmission stored in DB ${id.value}: $e"))
        case None =>
          ZIO.fail(RepositoryError.NotFound(s"ReceiptSubmission not found for id: ${id.value}"))
      }

  override def updateVerificationOutcome(submissionId: SubmissionId, status: SubmissionStatus, verification: VerificationOutcome): IO[RepositoryError, Unit] = {
    updateSubmissionState(
      submissionId,
      status,
      verification.statusDescription,
      set("verification", verification.toBson)
    )
  }

  override def updateBonusOutcome(submissionId: SubmissionId, status: SubmissionStatus, bonus: BonusOutcome): IO[RepositoryError, Unit] = {
    updateSubmissionState(
      submissionId,
      status,
      bonus.statusDescription,
      set("bonus", bonus.toBson)
    )
  }

  private def updateSubmissionState(submissionId: SubmissionId,
                                    status: SubmissionStatus,
                                    statusDescription: Option[String],
                                    additionalUpdates: Bson*): IO[RepositoryError, Unit] = {

    val mandatoryUpdates = List(set("status", status.toString))
    val optionalUpdate = statusDescription match {
      case Some(r) => set("statusDescription", r)
      case None => unset("statusDescription")
    }
    val allUpdates = combine(mandatoryUpdates ++ additionalUpdates :+ optionalUpdate: _*)

    val filter = equal("_id", submissionId.value)
    ZIO.fromFuture(_ => receiptSubmissions.updateOne(filter, allUpdates).toFuture())
      .flatMap { res =>
        if (res.getMatchedCount == 0)
          ZIO.fail(RepositoryError.NotFound(s"ReceiptSubmission $submissionId not found"))
        else
          ZIO.unit
      }
      .mapError { t => RepositoryError.UpdateError(s"Failed to update submission ${submissionId.value}. Cause: ${t.getMessage}", t) }
  }
}


object MongoReceiptSubmissionRepository {
  final val CollectionName = "receipt_submissions"

  val layer: ZLayer[MongoDatabase, Throwable, ReceiptSubmissionRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptSubmissionRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}