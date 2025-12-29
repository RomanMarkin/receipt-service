package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.SubmissionStatus
import com.betgol.receipt.integration.specs.BusinessLogicSpec.makeReceiptData
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import zio.*
import zio.http.*
import zio.json.*
import zio.test.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter


object BusinessLogicSpec extends ZIOSpecDefault with BasicIntegrationSpec {

  private val successfulPath: ZLayer[Any, Throwable, ReceiptService & Scope] =
    ZLayer.make[ReceiptService & Scope](
      SharedTestLayer.infraLayer,
      MockVerificationService.validDocPath,
      MockBonusService.bonusAssignedPath,
      ReceiptServiceLive.layer,
      Scope.default
    )

  private def parseReceiptResponse(response: Response): ZIO[Any, String, ReceiptSubmissionResponse] =
    for {
      body <- response.body.asString
        .mapError(err => s"Failed to parse API response: $err")
      dto <- ZIO.fromEither(body.fromJson[ReceiptSubmissionResponse])
        .mapError(err => s"Failed to parse API response: $err. Body was: $body")
    } yield dto

  override def spec = suite("Business Logic Validation")(

    test("Insufficient data fields (<7 fields)") {
      val insufficientDataFieldsGen = Gen.int(0, 6).flatMap { numSegments =>
        Gen.listOfN(numSegments)(Gen.alphaNumericString).map(_.mkString("|"))
      }
      check(insufficientDataFieldsGen) { invalidReceiptData =>
        val reqJson = ReceiptRequest(invalidReceiptData, "player-1", "PE").toJson
        val req = buildRequest(reqJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Insufficient data fields"))
        )
      }
    },

    test("Rejects invalid issuer tax id (RUC) formats (letters or wrong length)") {
      check(Gen.string.filter(s => s.length != 11 || !s.forall(_.isDigit))) { invalidRuc =>
        val payload = ReceiptRequest(makeReceiptData(issuerTaxId = invalidRuc), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid Issuer Tax Id (RUC)"))
        )
      }
    },

    test("Invalid document type (letters, wrong length, or wrong digits)") {
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
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid document type"))
        )
      }
    },

    test("Rejects invalid document series formats (invalid first letter or wrong length)") {
      val invalidSeriesGen = Gen.oneOf(
        Gen.alphaNumericString.filter(_.length != 4), //wrong length
        Gen.stringN(4)(Gen.alphaNumericChar)
          .filter(s => !s.startsWith("F") && !s.startsWith("B")), // wrong start char
        for {
          prefix <- Gen.elements("F", "B")
          suffix <- Gen.stringN(3)(Gen.alphaNumericChar).filter(s => !s.forall(_.isDigit))
        } yield s"$prefix$suffix", // wrong 2-4 digits
        Gen.elements("f001", "b999") // lowercase
      )

      check(invalidSeriesGen) { invalidDocSeries =>
        val payload = ReceiptRequest(makeReceiptData(docSeries = invalidDocSeries), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid document series"))
        )
      }
    },

    test("Rejects invalid document number formats (letters or wrong length)") {
      check(Gen.string.filter(s => s.length != 8 || !s.forall(_.isDigit))) { invalidDocNumber =>
        val payload = ReceiptRequest(makeReceiptData(docNumber = invalidDocNumber), "player-1", "PE").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid document number"))
        )
      }
    },

    test("Rejects invalid Total Amount") {
      val invalidAmounts = Gen.fromIterable(List("2.39.13", "ABC", "-50.00"))
      check(invalidAmounts) { badAmount =>
        val payload = ReceiptRequest(makeReceiptData(total = badAmount), "player-1", "PE").toJson
        val req = buildRequest(payload)
        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid total amount"))
        )
      }
    },

    test("Invalid date format (neigher yyyy-MM-dd, not dd/MM/yyyy)") {
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
          dto <- parseReceiptResponse(response)
        } yield assertTrue(
          response.status == Status.Ok,
          dto.receiptSubmissionId.nonEmpty,
          dto.status == SubmissionStatus.InvalidReceiptData.toString,
          dto.message.exists(_.contains("Invalid date format"))
        )
      }
    },

    test("Successful registration (supported date formats)") {
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
          dto <- parseReceiptResponse(response)
        } yield
          assertTrue(
            response.status == Status.Ok,
            dto.receiptSubmissionId.nonEmpty,
            dto.status == SubmissionStatus.BonusAssigned.toString,
            dto.message.isEmpty
          )
      }
    }

  ).provideLayerShared(successfulPath.orDie)
}
