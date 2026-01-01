package com.betgol.receipt.infrastructure.repos.mongo.mappers

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.models.*
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
      rs.verification.foreach { b =>
        doc.append("verification", b.toBson)
      }
      rs.bonus.foreach { b =>
        doc.append("bonus", b.toBson)
      }
      rs.statusDescription.foreach { reason =>
        doc.append("statusDescription", reason.toBsonString)
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
          .flatMap(_.toVerificationOutcome.toOption)

        bonusOpt = doc.getDocOpt("bonus")
          .flatMap(_.toBonusOutcome.toOption)

        statusDescriptionOpt = doc.getStringOpt("statusDescription")
      } yield ReceiptSubmission(
        id = id,
        status = status,
        metadata = metadata,
        fiscalDocument = fiscalDocOpt,
        verification = verification,
        bonus = bonusOpt,
        statusDescription = statusDescriptionOpt
      )
    }
  }

  // SubmissionMetadata Mappers

  extension (m: SubmissionMetadata) {
    def toBson: BsonDocument = new BsonDocument()
      .append("playerId", m.playerId.value.toBsonString)
      .append("country", m.country.value.toBsonString)
      .append("submittedAt", m.submittedAt.toBsonDateTime)
      .append("rawInput", m.rawInput.toBsonString)
  }

  extension (d: BsonDocument) {
    def toSubmissionMetadata: Either[String, SubmissionMetadata] =
      for {
        playerIdStr <- d.getStringOpt("playerId").toRight("Missing metadata.playerId")
        country <- d.getStringOpt("country").toRight("Missing country").flatMap(CountryCode.from)
        submittedAt <- d.getInstantOpt("submittedAt").toRight("Missing metadata.submittedAt")
        rawInput <- d.getStringOpt("rawInput").toRight("Missing metadata.rawInput")
      } yield SubmissionMetadata(PlayerId(playerIdStr), country, submittedAt, rawInput)
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
      } yield FiscalDocument(
        issuerTaxId = issuerTaxId,
        docType = docType,
        series = series,
        number = number,
        totalAmount = totalAmount,
        issuedAt = issuedAt
      )
    }
  }

  // VerificationConfirmation Mappers

  extension (c: VerificationOutcome) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("status", c.status.toString.toBsonString)
        .append("updatedAt", c.updatedAt.toBsonDateTime)
      c.statusDescription.foreach(s => doc.append("statusDescription", s.toBsonString))
      c.apiProvider.foreach(s => doc.append("apiProvider", s.toBsonString))
      c.externalId.foreach(id => doc.append("externalId", id.toBsonString))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toVerificationOutcome: Either[String, VerificationOutcome] = {
      for {
        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status <- Try(ReceiptVerificationStatus.valueOf(statusStr)).toOption.toRight(s"Invalid status: $statusStr")
        apiProvider = d.getStringOpt("apiProvider")
        updatedAt <- d.getInstantOpt("updatedAt").toRight("Missing updatedAt")
        statusDescription = d.getStringOpt("statusDescription")
        externalId = d.getStringOpt("externalId")
      } yield VerificationOutcome(
        status = status,
        statusDescription = statusDescription,
        apiProvider = apiProvider,
        updatedAt = updatedAt,
        externalId = externalId
      )
    }
  }

  // BonusOutcome Mappers

  extension (bo: BonusOutcome) {
    def toBson: BsonDocument = {
      val doc = new BsonDocument()
        .append("status", bo.status.toString.toBsonString)
        .append("updatedAt", bo.updatedAt.toBsonDateTime)
      bo.code.foreach(code => doc.append("code", BsonString(code.value)))
      bo.externalId.foreach(id => doc.append("externalId", BsonString(id)))
      bo.statusDescription.foreach(msg => doc.append("statusDescription", BsonString(msg)))
      doc
    }
  }

  extension (d: BsonDocument) {
    def toBonusOutcome: Either[String, BonusOutcome] = {
      for {
        statusStr <- d.getStringOpt("status").toRight("Missing status")
        status <- Try(BonusAssignmentStatus.valueOf(statusStr)).toOption.toRight(s"Invalid status: $statusStr")
        updatedAt <- d.getInstantOpt("updatedAt").toRight("Missing updatedAt")
        code = d.getStringOpt("code").map(BonusCode.apply)
        externalId = d.getStringOpt("externalId")
        statusDescription = d.getStringOpt("statusDescription")
      } yield BonusOutcome(
        code = code,
        status = status,
        updatedAt = updatedAt,
        externalId = externalId,
        statusDescription = statusDescription
      )
    }
  }

}