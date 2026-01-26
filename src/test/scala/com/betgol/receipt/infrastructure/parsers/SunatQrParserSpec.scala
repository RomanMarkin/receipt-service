package com.betgol.receipt.infrastructure.parsers

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.LocalDate


object SunatQrParserSpec extends ZIOSpecDefault {

  private val parser = SunatQrParser()

  // RUC|Type|Series|Number|IGV|Total|Date
  private val baseValid = "12345678901|01|F001|00001234|18.0|150.50|2024-11-30"

  override def spec = suite("SunatQrParserSpec")(

    suite("Happy Paths")(
      test("successfully parses a standard valid QR string (yyyy-MM-dd)") {
        val input = "20503840121|01|F756|00068781|36.48|239.13|2025-02-22|6|ignored"
        for {
          result <- parser.parse(input)
        } yield assertTrue(
          result.issuerTaxId == "20503840121",
          result.docType == "01",
          result.series == "F756",
          result.number == "00068781",
          result.totalAmount == 239.13,
          result.issuedAt == LocalDate.of(2025, 2, 22),
        )
      },

      test("successfully parses alphanumeric series (e.g. B1U3)") {
        val input = "20503840121|01|B1U3|00068781|0.0|100.00|2025-02-22"
        for {
          result <- parser.parse(input)
        } yield assertTrue(
          result.series == "B1U3"
        )
      },

      test("successfully parses date format dd/MM/yyyy") {
        // Changing date to 22/02/2025
        val input = "20503840121|01|F756|00068781|0.0|100.00|22/02/2025"
        for {
          result <- parser.parse(input)
        } yield assertTrue(
          result.issuedAt == LocalDate.of(2025, 2, 22)
        )
      },

      test("handles comma as decimal separator in total amount") {
        // "150,50" instead of "150.50"
        val input = "12345678901|01|F001|00001234|0|150,50|2024-01-01"
        for {
          result <- parser.parse(input)
        } yield assertTrue(
          result.totalAmount == 150.50
        )
      },

      test("ignores extra fields at the end (e.g. hash, customer info)") {
        val input = baseValid + "|6|12345678|DIGITAL_SIGNATURE_HASH"
        for {
          result <- parser.parse(input)
        } yield assertTrue(
          result.number == "00001234"
        )
      }
    ),

    suite("Validation Failures")(
      test("fails when string has insufficient fields (< 7)") {
        val input = "12345678901|01|F001" // Only 3 fields
        assertZIO(parser.parse(input).exit)(
          fails(equalTo("Insufficient data fields"))
        )
      },

      test("fails on invalid RUC (contains letters)") {
        val input = "A2345678901|01|F001|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid Issuer Tax Id"))
        )
      },

      test("fails on invalid RUC (wrong length)") {
        val input = "123|01|F001|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid Issuer Tax Id"))
        )
      },

      test("fails on invalid Document Type (not 01 or 03)") {
        val input = "12345678901|99|F001|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid document type: 99"))
        )
      },

      test("fails on invalid Series (starts with wrong char)") {
        val input = "12345678901|01|X001|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid document series: X001"))
        )
      },

      test("fails on invalid Series (wrong length)") {
        val input = "12345678901|01|F1|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid document series: F1"))
        )
      },

      test("fails on invalid Series (contains illegal special chars)") {
        // Dash '-' is not allowed in the regex [A-Z0-9]
        val input = "12345678901|01|F00-|00001234|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid document series: F00-"))
        )
      },

      test("fails on invalid Doc Number (letters)") {
        val input = "12345678901|01|F001|ABC12345|0|100|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid document number"))
        )
      },

      test("fails on invalid Total Amount (garbage)") {
        val input = "12345678901|01|F001|00001234|0|FREE|2024-01-01"
        assertZIO(parser.parse(input).exit)(
          fails(equalTo("Invalid total amount"))
        )
      },

      test("fails on invalid Date Format") {
        val input = "12345678901|01|F001|00001234|0|100|2024/Jan/01"
        assertZIO(parser.parse(input).exit)(
          fails(containsString("Invalid date format"))
        )
      }
    )
  )
}