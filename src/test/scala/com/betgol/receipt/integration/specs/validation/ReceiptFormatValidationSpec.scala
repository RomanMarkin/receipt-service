package com.betgol.receipt.integration.specs.validation

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.SubmissionStatus
import com.betgol.receipt.integration.specs.validation.ReceiptFormatValidationSpec.makeReceiptData
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import zio.http.*
import zio.json.*
import zio.test.*
import zio.{Scope, ZIO}

import java.time.LocalDate
import java.time.format.DateTimeFormatter


object ReceiptFormatValidationSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.validDocPath,
    MockBonusService.bonusAssignedPath,
  )

  private def parseApiResponse(response: Response): ZIO[Any, String, ReceiptSubmissionResponse] =
    for {
      body <- response.body.asString
        .mapError(err => s"Failed to parse API response: $err")
      apiResponse <- ZIO.fromEither(body.fromJson[ReceiptSubmissionResponse])
        .mapError(err => s"Failed to parse API response: $err. Body was: $body")
    } yield apiResponse

  def suiteSpec = suite("Domain Rules: Receipt Data Parsing")(

    test("Rejects receipt data string with missing segments (insufficient fields)") {
      val insufficientDataFieldsGen = Gen.int(0, 6).flatMap { numSegments =>
        Gen.listOfN(numSegments)(Gen.alphaNumericString).map(_.mkString("|"))
      }
      check(insufficientDataFieldsGen) { invalidReceiptData =>
        val reqJson = ReceiptRequest(invalidReceiptData, "player-1", "PE").toJson
        val req = buildRequest(reqJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Insufficient data fields"))
        )
      }
    },

    test("Rejects invalid Issuer Tax ID (RUC) format (non-numeric or wrong length)") {
      check(Gen.string.filter(s => s.length != 11 || !s.forall(_.isDigit))) { invalidRuc =>
        val payload = ReceiptRequest(makeReceiptData(issuerTaxId = invalidRuc), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid Issuer Tax Id (RUC)"))
        )
      }
    },

    test("Rejects invalid Document Type codes (must be '01' or '03')") {
      val invalidDocTypeGen = Gen.oneOf(
        Gen.alphaNumericString.filter(_.length != 2), //wrong length
        Gen.stringN(2)(Gen.alphaNumericChar)
          .filter(s => !s.equals("01") && !s.equals("03")), // 2 digits, but not 01 or 03
      )
      check(invalidDocTypeGen) { invalidDocType =>
        val payload = ReceiptRequest(makeReceiptData(docType = invalidDocType), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid document type"))
        )
      }
    },

    test("Rejects invalid Document Series format (must start with F/B followed by 3 alphanumeric chars)") {
      val invalidSeriesGen = Gen.oneOf(
        Gen.alphaNumericString.filter(_.length != 4),
        Gen.stringN(4)(Gen.alphaNumericChar)
          .filter(s => !s.startsWith("F") && !s.startsWith("B")),
        for {
          prefix <- Gen.elements("F", "B")
          suffix <- Gen.stringN(3)(Gen.alphaNumericChar).filter(s => !s.forall(_.isDigit))
        } yield s"$prefix$suffix",
        Gen.elements("f001", "b999") // lowercase
      )

      check(invalidSeriesGen) { invalidDocSeries =>
        val payload = ReceiptRequest(makeReceiptData(docSeries = invalidDocSeries), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid document series"))
        )
      }
    },

    test("Rejects invalid Document Number format (must be 8 digits)") {
      check(Gen.string.filter(s => s.length != 8 || !s.forall(_.isDigit))) { invalidDocNumber =>
        val payload = ReceiptRequest(makeReceiptData(docNumber = invalidDocNumber), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid document number"))
        )
      }
    },

    test("Rejects invalid or non-numeric Total Amount") {
      val invalidAmounts = Gen.fromIterable(List("2.39.13", "ABC", "-50.00"))
      check(invalidAmounts) { badAmount =>
        val payload = ReceiptRequest(makeReceiptData(total = badAmount), "player-1", "PE").toJson
        val req = buildRequest(payload)
        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid total amount"))
        )
      }
    },

    test("Rejects invalid Date formats (supports only yyyy-MM-dd or dd/MM/yyyy)") {
      val invalidDateGen: Gen[Any, String] = Gen.oneOf(
        Gen.const("2025/02/22"), // wrong separator for YYYY start
        Gen.const("22-02-2025"), // wrong separator for DD start
        Gen.const("02/22/2025"), // wrong format (MM/DD/YYYY)
        Gen.const("2025.02.22"), // wrong separator
        Gen.alphaNumericStringBounded(1, 10) // random string
      )
      check(invalidDateGen) { invalidDate =>
        val payload = ReceiptRequest(makeReceiptData(date = invalidDate), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.exists(_.contains("Invalid date format"))
        )
      }
    },

    test("Accepts and parses valid receipt data formats (Happy Path)") {
      val validReceiptGen: Gen[Any, String] = for {
        date <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        allowedPattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        validDate = date.format(DateTimeFormatter.ofPattern(allowedPattern))
        validIssuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        validDocNumber <- Gen.int(10000000, 99999999).map(_.toString)
        validReceipt = makeReceiptData(issuerTaxId = validIssuerTaxId, date = validDate, docNumber = validDocNumber)
      } yield validReceipt

      check(validReceiptGen) { validReceipt =>
        val payload = ReceiptRequest(validReceipt, "player-1", "PE").toJson
        val req = buildRequest(payload)
        
        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          apiResponse <- parseApiResponse(response)
        } yield
          assertTrue(
            response.status == Status.Ok,
            apiResponse.receiptSubmissionId.isValidUuid,
            apiResponse.status == SubmissionStatus.BonusAssigned.toString,
            apiResponse.message.isEmpty
          )
      }
    }

  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)
}
