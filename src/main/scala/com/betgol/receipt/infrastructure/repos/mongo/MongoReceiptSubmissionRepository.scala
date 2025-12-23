package com.betgol.receipt.infrastructure.repos.mongo

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.models.{BonusOutcome, ReceiptSubmission, SubmissionStatus, VerificationOutcome}
import com.betgol.receipt.domain.repos.ReceiptSubmissionRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.ReceiptSubmissionMappers.toBson
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{Filters, IndexOptions}
import zio.*


case class MongoReceiptSubmissionRepository(db: MongoDatabase) extends ReceiptSubmissionRepository {
  import MongoReceiptSubmissionRepository.CollectionName
  private val receiptSubmissions = db.getCollection(CollectionName)

  def ensureIndexes: Task[Unit] = {
    val key = compoundIndex(
      ascending("fiscalDocument.country"),
      ascending("fiscalDocument.issuerTaxId"),
      ascending("fiscalDocument.type"),
      ascending("fiscalDocument.series"),
      ascending("fiscalDocument.number")
    )
    val options = IndexOptions()
      .unique(true)
      .name("unique_receipt_idx")
      .partialFilterExpression(Filters.exists("fiscalDocument"))
    for {
      _ <- ZIO.fromFuture(_ => receiptSubmissions.createIndex(key, options).toFuture())
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

  override def updateVerificationOutcome(submissionId: SubmissionId, status: SubmissionStatus, verification: VerificationOutcome): IO[RepositoryError, Unit] = {
    val filter = equal("_id", submissionId.value)
    val update = combine(
      set("status", status.toString),
      set("verification", verification.toBson)
    )
    ZIO.fromFuture(_ => receiptSubmissions.updateOne(filter, update).toFuture())
      .flatMap { res =>
        if (res.getModifiedCount == 0) ZIO.fail(RepositoryError.NotFound(s"ReceiptSubmission $submissionId not found"))
        else ZIO.unit
      }
      .mapError { t => RepositoryError.UpdateError(s"Failed to update verification outcome for ReceiptSubmission ${submissionId.value}. Cause: ${t.getMessage}", t) }
  }

  override def updateBonusOutcome(submissionId: SubmissionId, status: SubmissionStatus, bonus: BonusOutcome): IO[RepositoryError, Unit] = {
    val filter = equal("_id", submissionId.value)
    val update = combine(
      set("status", status.toString),
      set("bonus", bonus.toBson)
    )
    ZIO.fromFuture(_ => receiptSubmissions.updateOne(filter, update).toFuture())
      .flatMap { res =>
        if (res.getModifiedCount == 0) ZIO.fail(RepositoryError.NotFound(s"ReceiptSubmission $submissionId not found"))
        else ZIO.unit
      }
      .mapError { t => RepositoryError.UpdateError(s"Failed to update bonus outcome for ReceiptSubmission ${submissionId.value}. Cause: ${t.getMessage}", t) }
  }
}


object MongoReceiptSubmissionRepository {
  final val CollectionName = "receipt_submission"

  val layer: ZLayer[MongoDatabase, Throwable, ReceiptSubmissionRepository] =
    ZLayer.fromZIO {
      for {
        db <- ZIO.service[MongoDatabase]
        repo = MongoReceiptSubmissionRepository(db)
        _ <- repo.ensureIndexes
      } yield repo
    }
}