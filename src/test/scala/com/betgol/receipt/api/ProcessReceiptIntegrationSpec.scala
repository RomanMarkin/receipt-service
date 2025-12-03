package com.betgol.receipt.api

import com.betgol.receipt.TestMongoLayer
import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.ReceiptRequest
import com.betgol.receipt.infrastructure.parsing.SunatQrParser
import com.betgol.receipt.infrastructure.repo.{MongoReceiptRepository, MongoReceiptRetryRepository}
import com.betgol.receipt.service.ReceiptServiceLive
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.json.*
import zio.test.*


object ProcessReceiptIntegrationSpec extends ZIOSpecDefault {

  // Setup config provider for reading test application.conf
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  // Full application layer with test DB
  private val testLayer =
    TestMongoLayer.layer >+> (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    SunatQrParser.layer >+> ReceiptServiceLive.layer

  // Helper to build a request
  private def buildRequest(body: String): Request =
    Request.post(URL(Path.root / "processReceipt"), Body.fromString(body))

  // Sample valid sata
  private def randomIssuerTaxId = List.fill(11)(scala.util.Random.nextInt(10)).mkString
  private def validReceipt = s"$randomIssuerTaxId|01|F756|00068781|36.48|239.13|2025-02-22|6|hash"
  val playerId = "player-123"

  override def spec = suite("/processReceipt endpoint integration tests")(

    test("Scenario 1.1: Invalid JSON passed (Corrupted JSON)") {
      val badJson = """{"Corrupted" "JSON"}"""
      val req = buildRequest(badJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid JSON")
      )
    },

    test("Scenario 1.2: Invalid JSON passed (Unexpected JSON structure)") {
      val badJson = """{"wrongField": "someData"}"""
      val req = buildRequest(badJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid JSON")
      )
    },

    test("Scenario 2.1: Business Validation (Insufficient data fields)") {
      val invalidReceiptData = "12345678901|00|F756" //not enough fields in raw string
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Insufficient data fields")
      )
    },

    test("Scenario 2.2.1: Business Validation (Invalid Issuer Tax Id (RUC) -- contains letters)") {
      //00 -- invalid receipt type
      val invalidReceiptData = "A01234567890|00|F756|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid Issuer Tax Id (RUC)")
      )
    },

    test("Scenario 2.2.2: Business Validation (Invalid Issuer Tax Id (RUC) -- less than 11 digits)") {
      val invalidReceiptData = "1234567890|00|F756|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid Issuer Tax Id (RUC)")
      )
    },

    test("Scenario 2.2.3: Business Validation (Invalid Issuer Tax Id (RUC) -- more than 11 digits)") {
      val invalidReceiptData = "123456789012|00|F756|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid Issuer Tax Id (RUC)")
      )
    },

    test("Scenario 2.3: Business Validation (Invalid document type)") {
      val invalidReceiptData = "12345678901|00|F756|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document type")
      )
    },

    test("Scenario 2.4.1: Business Validation (Invalid document series - Wrong starting character)") {
      val invalidReceiptData = "12345678901|01|X756|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document series")
      )
    },

    test("Scenario 2.4.2: Business Validation (Invalid document series - Invalid series length)") {
      val invalidReceiptData = "12345678901|01|F100000|00068781|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document series")
      )
    },

    test("Scenario 2.5.1: Business Validation (Invalid document number -- less than 8 digits)") {
      val invalidReceiptData = "12345678901|01|F756|1234567|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document number")
      )
    },

    test("Scenario 2.5.1: Business Validation (Invalid document number -- more than 8 digits)") {
      val invalidReceiptData = "12345678901|01|F756|123456789|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document number")
      )
    },

    test("Scenario 2.5.1: Business Validation (Invalid document number -- contains letters)") {
      val invalidReceiptData = "12345678901|01|F756|A1234567|36.48|239.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid document number")
      )
    },

    test("Scenario 2.6.1: Business Validation (Invalid total amount)") {
      val invalidReceiptData = "12345678901|01|F756|12345678|36.48|2.39.13|2025-02-22|6"
      val reqJson = ReceiptRequest(invalidReceiptData, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid total amount")
      )
    },

    test("Scenario 3.1: Successful Registration (date format yyyy-MM-dd)") {
      val reqJson = ReceiptRequest(validReceipt, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        bodyStr.contains("Receipt accepted")
      )
    },

    test("Scenario 3.2: Successful Registration (date format dd/MM/yyyy)") {
      val validReceipt = s"$randomIssuerTaxId|01|F756|00068781|36.48|239.13|22/02/2025|6|hash"
      val reqJson = ReceiptRequest(validReceipt, playerId).toJson
      val req = buildRequest(reqJson)

      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        bodyStr.contains("Receipt accepted")
      )
    },

    test("Scenario 4: Duplication (DB constraint check)") {
      val uniqueReceipt = "12345678901|01|F756|99999999|36.48|100.00|2025-02-22|6"
      val reqJson = ReceiptRequest(uniqueReceipt, playerId).toJson
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
    Scope.default >+> testLayer.orDie
  )
}