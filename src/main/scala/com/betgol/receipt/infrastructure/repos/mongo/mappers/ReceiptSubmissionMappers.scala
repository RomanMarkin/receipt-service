package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import org.mongodb.scala.bson.{BsonDocument, BsonString}

import scala.util.Try


object ReceiptSubmissionMappers {

  // ReceiptSubmission Mappers

  extension (rs: ReceiptSubmission) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("_id", rs.id.value.toBsonString)
        .append("status", rs.status.toString.toBsonString)
        .append("metadata", rs.metadata.toBson)
      rs.fiscalDocument.foreach { fd =>
        doc.append("fiscalDocument", fd.toBson)
      }
      rs.bonus.foreach { b =>
        doc.append("bonus", b.toBson)
      }
      rs.failureReason.foreach { reason =>
        doc.append("failureReason", reason.toBsonString)
      }
      doc
    }
  }

  extension (doc: BsonDocument) {
    def toReceiptSubmission: Either[String, ReceiptSubmission] = {
      for {
        id <- doc.getStringOpt("_id")
          .map(SubmissionId(_))
          .toRight("Missing or invalid _id")

        statusStr <- doc.getStringOpt("status").toRight("Missing status")
        status <- Try(SubmissionStatus.valueOf(statusStr)).toOption
          .toRight(s"Invalid submission status: $statusStr")

        metaDoc <- doc.getDocOpt("metadata").toRight("Missing metadata")
        metadata <- metaDoc.toSubmissionMetadata

        fiscalDocOpt = doc.getDocOpt("fiscalDocument")
          .flatMap(_.toFiscalDocument.toOption)

        verification = doc.getDocOpt("verification")
          .flatMap(_.toVerificationConfirmation.toOption)

        bonusOpt = doc.getDocOpt("bonus")
          .flatMap(_.toBonusOutcome.toOption)

        failureReasonOpt = doc.getStringOpt("failureReason")
      } yield ReceiptSubmission(
        id = id,
        status = status,
        metadata = metadata,
        fiscalDocument = fiscalDocOpt,
        verification = verification,
        bonus = bonusOpt,
        failureReason = failureReasonOpt
      )
    }
  }

  // SubmissionMetadata Mappers

  extension (m: SubmissionMetadata) {
    def toBson: BsonDocument = new BsonDocument()
      .append("playerId", m.playerId.value.toBsonString)
      .append("submittedAt", m.submittedAt.toBsonDateTime)
      .append("rawInput", m.rawInput.toBsonString)
  }

  extension (d: BsonDocument) {
    def toSubmissionMetadata: Either[String, SubmissionMetadata] =
      for {
        pidStr <- d.getStringOpt("playerId").toRight("Missing metadata.playerId")
        submittedAt <- d.getInstantOpt("submittedAt").toRight("Missing metadata.submittedAt")
        rawInput <- d.getStringOpt("rawInput").toRight("Missing metadata.rawInput")
      } yield SubmissionMetadata(PlayerId(pidStr), submittedAt, rawInput)
  }

  // FiscalDocument Mappers

  extension (fd: FiscalDocument) {
    def toBson: BsonDocument = new BsonDocument()
      .append("issuerTaxId", fd.issuerTaxId.toBsonString)
      .append("type", fd.docType.toBsonString)
      .append("series", fd.series.toBsonString)
      .append("number", fd.number.toBsonString)
      .append("totalAmount", fd.totalAmount.toBsonDecimal128)
      .append("issuedAt", fd.issuedAt.toBsonDateTime)
      .append("country", fd.country.value.toBsonString)
  }

  extension (d: BsonDocument) {
    def toFiscalDocument: Either[String, FiscalDocument] = {
      for {
        issuerTaxId <- d.getStringOpt("issuerTaxId").toRight("Missing issuerTaxId")
        docType <- d.getStringOpt("type").toRight("Missing type")
        series <- d.getStringOpt("series").toRight("Missing series")
        number <- d.getStringOpt("number").toRight("Missing number")
        totalAmount <- d.getBigDecimalOpt("totalAmount").toRight("Missing or invalid totalAmount")
        issuedAt    <- d.getLocalDateOpt("issuedAt").toRight("Missing issuedAt")
        country <- d.getStringOpt("country").toRight("Missing country").flatMap(CountryCode.from)
      } yield FiscalDocument(
        issuerTaxId = issuerTaxId,
        docType = docType,
        series = series,
        number = number,
        totalAmount = totalAmount,
        issuedAt = issuedAt,
        country = country
      )
    }
  }

  // VerificationConfirmation Mappers

  extension (c: VerificationConfirmation) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("apiProvider", c.apiProvider.toBsonString)
        .append("confirmedAt", c.confirmedAt.toBsonDateTime)
        .append("statusMessage", c.statusMessage.toBsonString)
      c.externalId.foreach(id => doc.append("externalId", id.toBsonString))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toVerificationConfirmation: Either[String, VerificationConfirmation] = {
      for {
        apiProvider <- d.getStringOpt("apiProvider").toRight("Missing apiProvider")
        confirmedAt <- d.getInstantOpt("confirmedAt").toRight("Missing confirmedAt")
        externalId = d.getStringOpt("externalId")
        statusMessage <- d.getStringOpt("statusMessage").toRight("Missing statusMessage")
      } yield VerificationConfirmation(
        apiProvider = apiProvider,
        confirmedAt = confirmedAt,
        externalId = externalId,
        statusMessage = statusMessage
      )
    }
  }

  // BonusOutcome Mappers

  extension (bo: BonusOutcome) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("code", bo.code.toString.toBsonString)
        .append("status", bo.status.toString.toBsonString)
        .append("assignedAt", bo.assignedAt.toBsonDateTime)
      bo.externalId.foreach(id => doc.append("externalId", BsonString(id)))
      bo.statusMessage.foreach(msg => doc.append("statusMessage", BsonString(msg)))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toBonusOutcome: Either[String, BonusOutcome] = {
      for {
        code <- d.getStringOpt("code").map(BonusCode.apply).toRight("Missing code")
        statusStr <- d.getStringOpt("status").toRight("Missing bonus status")
        status <- Try(BonusAssignmentStatus.valueOf(statusStr)).toOption.toRight(s"Invalid bonus status: $statusStr")
        assignedAt <- d.getInstantOpt("assignedAt").toRight("Missing assignedAt")
        externalId = d.getStringOpt("externalId")
        statusMessage = d.getStringOpt("statusMessage")
      } yield BonusOutcome(
        code = code,
        status = status,
        assignedAt = assignedAt,
        externalId = externalId,
        statusMessage = statusMessage
      )
    }
  }

}
