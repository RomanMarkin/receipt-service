package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.ReceiptRequest
import com.betgol.receipt.integration.specs.BusinessLogicSpec.makeReceipt
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import zio.*
import zio.http.*
import zio.json.EncoderOps
import zio.test.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter


object BusinessLogicSpec extends ZIOSpecDefault with BasicIntegrationSpec {

  override def spec = suite("Business Logic Validation")(

    test("Insufficient data fields (<7 fields)") {
      val insufficientDataFieldsGen = Gen.int(0, 6).flatMap { numSegments =>
        Gen.listOfN(numSegments)(Gen.alphaNumericString).map(_.mkString("|"))
      }
      check(insufficientDataFieldsGen) { invalidReceiptData =>
        val reqJson = ReceiptRequest(invalidReceiptData, "player-1").toJson
        val req = buildRequest(reqJson)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          bodyStr <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          bodyStr.contains("Insufficient data fields")
        )
      }
    },

    test("Rejects invalid issuer tax id (RUC) formats (letters or wrong length)") {
      check(Gen.string.filter(s => s.length != 11 || !s.forall(_.isDigit))) { invalidRuc =>
        val payload = ReceiptRequest(makeReceipt(issuerTaxId = invalidRuc), "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          body.contains("Invalid Issuer Tax Id (RUC)")
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
        val payload = ReceiptRequest(makeReceipt(docType = invalidDocType), "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          body.contains("Invalid document type")
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
        val payload = ReceiptRequest(makeReceipt(docSeries = invalidDocSeries), "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          body.contains("Invalid document series")
        )
      }
    },

    test("Rejects invalid document number formats (letters or wrong length)") {
      check(Gen.string.filter(s => s.length != 8 || !s.forall(_.isDigit))) { invalidDocNumber =>
        val payload = ReceiptRequest(makeReceipt(docNumber = invalidDocNumber), "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          body.contains("Invalid document number")
        )
      }
    },

    test("Rejects invalid Total Amount") {
      val invalidAmounts = Gen.fromIterable(List("2.39.13", "ABC", "-50.00"))
      check(invalidAmounts) { badAmount =>
        val payload = ReceiptRequest(makeReceipt(total = badAmount), "player-1").toJson
        val req = buildRequest(payload)
        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(body.contains("Invalid total amount"))
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
        val payload = ReceiptRequest(makeReceipt(date = invalidDate), "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.BadRequest,
          body.contains("Invalid date format")
        )
      }
    },

    test("Successful registration (supported date formats)") {
      val validReceiptGen: Gen[Any, String] = for {
        date <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        allowedPattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
        validDate = date.format(DateTimeFormatter.ofPattern(allowedPattern))
        validIssuerTaxId <- Gen.stringN(11)(Gen.numericChar)
        validReceipt = makeReceipt(issuerTaxId = validIssuerTaxId, date = validDate)
      } yield validReceipt

      check(validReceiptGen) { validReceipt =>
        val payload = ReceiptRequest(validReceipt, "player-1").toJson
        val req = buildRequest(payload)

        for {
          response <- ReceiptRoutes.routes.runZIO(req)
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("Receipt accepted")
        )
      }
    }

  ).provideLayerShared(Scope.default >+> SharedTestLayer.layer.orDie)
}
