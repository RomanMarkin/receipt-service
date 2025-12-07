package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.ReceiptRequest
import com.betgol.receipt.domain.parsing.ReceiptParser
import com.betgol.receipt.domain.repo.{ReceiptRepository, ReceiptRetryRepository}
import com.betgol.receipt.domain.{ReceiptRetryStatus, ReceiptStatus}
import com.betgol.receipt.infrastructure.repo.MongoMappers.toMongoDate
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import com.betgol.receipt.mocks.clients.MockFiscalClientProvider
import com.betgol.receipt.service.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters.{equal, and}
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

    test("Successful receipt registration saves correct document structure") {
      val validReceiptDataGen = for {
        issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        docType = "01"
        docSeries <- Gen.elements("F001", "B001")
        docNumber <- Gen.stringN(8)(Gen.numericChar)
        total <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
        dateObj <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        pattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        dateStr = dateObj.format(DateTimeFormatter.ofPattern(pattern))
        receiptData = makeReceipt(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
      } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(receiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _        <- ZIO.succeed(assertTrue(response.status == Status.Ok))

          db       <- ZIO.service[MongoDatabase]
          receipts = db.getCollection("receipt")
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          savedDoc <- ZIO.fromFuture(_ =>
              receipts.find(
                and(
                  equal("document.issuerTaxId", expectedIssuerTaxId),
                  equal("document.type", expectedDocType),
                  equal("document.series", expectedDocSeries),
                  equal("document.number", expectedDocNumber)
                )
              ).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Test failure: Could not find the receipt in MongoDB by document.issuerTaxId = $expectedIssuerTaxId, document.type = $expectedDocType, document.series = $expectedDocSeries, document.number = $expectedDocNumber")

          docMeta    = savedDoc.get("document", classOf[org.bson.Document])
          reqMeta    = savedDoc.get("request", classOf[org.bson.Document])
        } yield assertTrue(
          savedDoc.containsKey("_id"),
          savedDoc.getString("status") == ReceiptStatus.Verified.toString,

          reqMeta.getString("playerId") == playerId,
          reqMeta.getString("rawData") == receiptData,
          abs(ChronoUnit.SECONDS.between(
            reqMeta.getDate("requestDate").toInstant,
            Instant.now())
          ) < 5,

          docMeta.getString("country") == "PE",
          docMeta.getString("issuerTaxId") == expectedIssuerTaxId,
          docMeta.getString("type") == expectedDocType,
          docMeta.getString("series") == expectedDocSeries,
          docMeta.getString("number") == expectedDocNumber,
          docMeta.getDate("date") == expectedDateObj.toMongoDate,
          docMeta.getDouble("totalAmount") == expectedTotal.toDouble

        )
      }
    },

    test("Invalid receipt registration request is saved with error") {
      val invalidReceiptData = Gen.stringN(100)(Gen.alphaNumericChar)

      check(invalidReceiptData) { invalidReceiptData =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(invalidReceiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _ <- ZIO.succeed(assertTrue(response.status == Status.BadRequest))

          db <- ZIO.service[MongoDatabase]
          receipts = db.getCollection("receipt")
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          savedDoc <- ZIO.fromFuture(_ =>
            receipts.find(equal("request.rawData", invalidReceiptData)).headOption()
          )
          .some
          .retry(retryPolicy)
          .orElseFail(s"Test failure: Could not find the receipt in MongoDB by request.rawData = $invalidReceiptData")

          docMetaOpt = savedDoc.get("document")
          reqMeta = savedDoc.get("request", classOf[org.bson.Document])
        } yield assertTrue(
          savedDoc.containsKey("_id"),
          savedDoc.getString("status") == ReceiptStatus.InvalidReceiptData.toString,
          savedDoc.getString("errorDetail") != null,
          savedDoc.getString("errorDetail").nonEmpty,

          reqMeta.getString("playerId") == playerId,
          reqMeta.getString("rawData") == invalidReceiptData,
          abs(ChronoUnit.SECONDS.between(
            reqMeta.getDate("requestDate").toInstant,
            Instant.now())
          ) < 5,

          docMetaOpt.isEmpty
        )
      }
    },

    test("Unverified receipt is saved with retry record") {
      val validReceiptDataGen = for {
        issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        docType = "01"
        docSeries <- Gen.elements("F002", "B002")
        docNumber <- Gen.stringN(8)(Gen.numericChar)
        total <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
        dateObj <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        pattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        dateStr = dateObj.format(DateTimeFormatter.ofPattern(pattern))
        receiptData = makeReceipt(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
      } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val req = buildRequest(ReceiptRequest(receiptData, playerId).toJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          _ <- ZIO.succeed(assertTrue(response.status == Status.Ok))

          db <- ZIO.service[MongoDatabase]
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          receipts = db.getCollection("receipt")
          savedDoc <- ZIO.fromFuture(_ =>
              receipts.find(
                and(
                  equal("document.issuerTaxId", expectedIssuerTaxId),
                  equal("document.type", expectedDocType),
                  equal("document.series", expectedDocSeries),
                  equal("document.number", expectedDocNumber)
                )
              ).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Test failure: Could not find the receipt in MongoDB by document.issuerTaxId = $expectedIssuerTaxId, document.type = $expectedDocType, document.series = $expectedDocSeries, document.number = $expectedDocNumber")

          receiptId = savedDoc.get("_id", classOf[org.bson.types.ObjectId])
          docMeta = savedDoc.get("document", classOf[org.bson.Document])
          reqMeta = savedDoc.get("request", classOf[org.bson.Document])

          receiptRetries = db.getCollection("receipt_retry")

          receiptRetryDoc <- ZIO.fromFuture(_ =>
              receiptRetries.find(equal("receiptId", receiptId)).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Test failure: Could not find the receipt retry in MongoDB by receiptId = $receiptId")

        } yield assertTrue(
          savedDoc.containsKey("_id"),
          savedDoc.getString("status") == ReceiptStatus.VerificationPending.toString,

          reqMeta.getString("playerId") == playerId,
          reqMeta.getString("rawData") == receiptData,
          abs(ChronoUnit.SECONDS.between(
            reqMeta.getDate("requestDate").toInstant,
            Instant.now())
          ) < 5,

          docMeta.getString("country") == "PE",
          docMeta.getString("issuerTaxId") == expectedIssuerTaxId,
          docMeta.getString("type") == expectedDocType,
          docMeta.getString("series") == expectedDocSeries,
          docMeta.getString("number") == expectedDocNumber,
          docMeta.getDate("date") == expectedDateObj.toMongoDate,
          docMeta.getDouble("totalAmount") == expectedTotal.toDouble,

          receiptRetryDoc.getObjectId ("receiptId") == receiptId,
          receiptRetryDoc.getString("playerId") == playerId,
          receiptRetryDoc.getString("country") == "PE",
          abs(ChronoUnit.SECONDS.between(
            receiptRetryDoc.getDate("addedAt").toInstant,
            Instant.now())
          ) < 5,
          receiptRetryDoc.getInteger("attempts") == 0,
          receiptRetryDoc.getString("status") == ReceiptRetryStatus.Pending.toString
        )
      }

    }.provideSome[Scope & MongoDatabase & ReceiptRepository & ReceiptRetryRepository & ReceiptParser]( //the rest layers are provided at the test-suite level
      MockFiscalClientProvider.docNotFoundPath,
      ReceiptServiceLive.layer
    ),

    test("Prevent document duplications (country + issuerTaxId + type + series + number)") {
      val reqJson = ReceiptRequest(makeReceipt(), "player-id").toJson
      val req = buildRequest(reqJson)

      for {
        resp1 <- ReceiptRoutes.routes.runZIO(req)
        resp2 <- ReceiptRoutes.routes.runZIO(req)
        body2 <- resp2.body.asString
      } yield assertTrue(
        resp1.status == Status.Ok,
        resp2.status == Status.Conflict,
        body2.contains("already processed")
      )
    }
  ).provideLayerShared(
    Scope.default >+>
    TestConfig.live(repeats = 0, retries = 0, shrinks = 1, samples = 1) >+>
    SharedTestLayer.infrastructure.orDie >+>
    MockFiscalClientProvider.happyPath >+>
    ReceiptServiceLive.layer
  ) @@ TestAspect.withLiveClock @@ TestAspect.sequential
}
