package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.ReceiptRequest
import com.betgol.receipt.domain.clients.BettingApiClient
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptSubmissionRepository, VerificationRetryRepository}
import com.betgol.receipt.domain.services.{BonusEvaluator, IdGenerator}
import com.betgol.receipt.domain.{SubmissionStatus, VerificationRetryStatus}
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.infrastructure.repos.mongo.{MongoReceiptSubmissionRepository, MongoVerificationRetryRepository}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import com.betgol.receipt.mocks.clients.{MockBettingXmlApiClient, MockFiscalClientProvider}
import com.betgol.receipt.services.ReceiptServiceLive
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal}
import zio.*
import zio.http.*
import zio.json.EncoderOps
import zio.test.*

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}
import scala.math.abs


object PersistenceSpec extends ZIOSpecDefault with BasicIntegrationSpec {

  override def spec = suite("Persistence (DB writes, DB constraints)")(

    test("Successful receipt submission saves correct document structure") {
      val validReceiptDataGen = for {
        issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        docType     = "01"
        docSeries   <- Gen.elements("F001", "B001")
        docNumber   <- Gen.stringN(8)(Gen.numericChar)
        total       <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
        dateObj     <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        pattern     <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        dateStr     = dateObj.format(DateTimeFormatter.ofPattern(pattern))
        receiptData = makeReceiptData(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
      } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(receiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _        <- ZIO.succeed(assertTrue(response.status == Status.Ok))
          db       <- ZIO.service[MongoDatabase]

          receipts = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          savedDoc <- ZIO.fromFuture(_ =>
              receipts.find(
                and(
                  equal("fiscalDocument.issuerTaxId", expectedIssuerTaxId),
                  equal("fiscalDocument.type", expectedDocType),
                  equal("fiscalDocument.series", expectedDocSeries),
                  equal("fiscalDocument.number", expectedDocNumber)
                )
              ).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Receipt not found for issuer: $expectedIssuerTaxId, number: $expectedDocNumber")

          fiscalDocOpt = savedDoc.getDocOpt("fiscalDocument")
          metadataOpt  = savedDoc.getDocOpt("metadata")

        } yield assertTrue(
          // ReceiptSubmission
          savedDoc.getStringOpt("_id").isDefined,
          savedDoc.getStringOpt("status").contains(SubmissionStatus.ValidatedNoBonus.toString),

          // Metadata
          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(receiptData),
          abs(ChronoUnit.SECONDS.between(
            metadataOpt.flatMap(_.getInstantOpt("submittedAt")).getOrElse(Instant.MIN),
            Instant.now()
          )) < 5,

          // Fiscal Document
          fiscalDocOpt.isDefined,
          fiscalDocOpt.flatMap(_.getStringOpt("country")).contains("PE"),
          fiscalDocOpt.flatMap(_.getStringOpt("issuerTaxId")).contains(expectedIssuerTaxId),
          fiscalDocOpt.flatMap(_.getStringOpt("type")).contains(expectedDocType),
          fiscalDocOpt.flatMap(_.getStringOpt("series")).contains(expectedDocSeries),
          fiscalDocOpt.flatMap(_.getStringOpt("number")).contains(expectedDocNumber),
          fiscalDocOpt.flatMap(_.getLocalDateOpt("issuedAt")).contains(expectedDateObj),
          fiscalDocOpt.flatMap(_.getBigDecimalOpt("totalAmount")).map(_.toString).contains(expectedTotal)
        )
      }
    },

    test("Invalid receipt submission is saved with error") {
      val invalidReceiptDataGen = Gen.stringN(100)(Gen.alphaNumericChar)

      check(invalidReceiptDataGen) { invalidReceiptData =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(invalidReceiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _        <- ZIO.succeed(assertTrue(response.status == Status.BadRequest))

          db       <- ZIO.service[MongoDatabase]
          receipts = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          savedDoc <- ZIO.fromFuture(_ =>
              receipts.find(equal("metadata.rawInput", invalidReceiptData)).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Receipt not found for rawInput: $invalidReceiptData")

          metadataOpt = savedDoc.getDocOpt("metadata")

        } yield assertTrue(
          savedDoc.getStringOpt("_id").isDefined,
          savedDoc.getStringOpt("status").contains(SubmissionStatus.InvalidReceiptData.toString),
          savedDoc.getStringOpt("failureReason").exists(_.nonEmpty),

          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(invalidReceiptData),
          abs(ChronoUnit.SECONDS.between(
            metadataOpt.flatMap(_.getInstantOpt("submittedAt")).getOrElse(Instant.MIN),
            Instant.now()
          )) < 5,

          savedDoc.getDocOpt("fiscalDocument").isEmpty,
          savedDoc.getDocOpt("bonus").isEmpty
        )
      }
    },

    test("Unverified receipt is saved with retry record") {
      val validReceiptDataGen = for {
        issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        docType     = "01"
        docSeries   <- Gen.elements("F002", "B002")
        docNumber   <- Gen.stringN(8)(Gen.numericChar)
        total       <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
        dateObj     <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        pattern     <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        dateStr     = dateObj.format(DateTimeFormatter.ofPattern(pattern))
        receiptData = makeReceiptData(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
      } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(receiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _        <- ZIO.succeed(assertTrue(response.status == Status.Ok))
          db          <- ZIO.service[MongoDatabase]
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)
          receipts    = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)

          savedDoc <- ZIO.fromFuture(_ =>
              receipts.find(
                and(
                  equal("fiscalDocument.issuerTaxId", expectedIssuerTaxId),
                  equal("fiscalDocument.type", expectedDocType),
                  equal("fiscalDocument.series", expectedDocSeries),
                  equal("fiscalDocument.number", expectedDocNumber)
                )
              ).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail("Receipt not found")

          submissionIdOpt = savedDoc.getStringOpt("_id")
          fiscalDocOpt    = savedDoc.getDocOpt("fiscalDocument")
          metadataOpt     = savedDoc.getDocOpt("metadata")

          // Verification Retry Check
          verificationRetries = db.getCollection[BsonDocument](MongoVerificationRetryRepository.CollectionName)
          retryDoc <- ZIO.fromFuture(_ =>
              verificationRetries.find(equal("submissionId", submissionIdOpt.getOrElse("unknown"))).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Retry record not found for submissionId: ${submissionIdOpt.getOrElse("unknown")}")

        } yield assertTrue(
          submissionIdOpt.isDefined,
          savedDoc.getStringOpt("status").contains(SubmissionStatus.VerificationPending.toString),

          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          fiscalDocOpt.flatMap(_.getStringOpt("issuerTaxId")).contains(expectedIssuerTaxId),
          fiscalDocOpt.flatMap(_.getLocalDateOpt("issuedAt")).contains(expectedDateObj),
          fiscalDocOpt.flatMap(_.getBigDecimalOpt("totalAmount")).map(_.toString).contains(expectedTotal),

          // Retry Assertions
          retryDoc.getStringOpt("submissionId") == submissionIdOpt, // Check match
          retryDoc.getStringOpt("playerId").contains(playerId),
          retryDoc.getStringOpt("country").contains("PE"),
          retryDoc.getIntOpt("attempt").contains(1),
          retryDoc.getStringOpt("status").contains(VerificationRetryStatus.Pending.toString),

          abs(ChronoUnit.SECONDS.between(
            retryDoc.getInstantOpt("addedAt").getOrElse(Instant.MIN),
            Instant.now()
          )) < 5
        )
      }
    }.provideSome[Scope & MongoDatabase & ReceiptSubmissionRepository & VerificationRetryRepository & BonusAssignmentRepository & BettingApiClient & ReceiptParser & IdGenerator & BonusEvaluator](
      MockFiscalClientProvider.docNotFoundPath,
      ReceiptServiceLive.layer
    ),

    test("Prevent document duplications (country + issuerTaxId + type + series + number)") {
      val uniqueNumberGen = Gen.stringN(8)(Gen.numericChar)

      check(uniqueNumberGen) { uniqueNumber =>
        val receiptData = makeReceiptData(docNumber = uniqueNumber)
        val req = buildRequest(ReceiptRequest(receiptData, "player-id").toJson)

        for {
          resp1 <- ReceiptRoutes.routes.runZIO(req)
          resp2 <- ReceiptRoutes.routes.runZIO(req) // Duplicate submission
          body2 <- resp2.body.asString
        } yield assertTrue(
          resp1.status == Status.Ok,
          resp2.status == Status.Conflict,
          body2.contains("already processed")
        )
      }
    }
  ).provideLayerShared(
    Scope.default >+>
      TestConfig.live(repeats = 0, retries = 0, shrinks = 1, samples = 1) >+>
      SharedTestLayer.infrastructure.orDie >+>
      MockFiscalClientProvider.happyPath >+>
      MockBettingXmlApiClient.layer >+>
      (UuidV7IdGenerator.layer ++ HardcodedBonusEvaluator.layer) >+>
      ReceiptServiceLive.layer
  ) @@ TestAspect.withLiveClock @@ TestAspect.sequential
}
