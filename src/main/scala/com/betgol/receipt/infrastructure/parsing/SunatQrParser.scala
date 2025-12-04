package com.betgol.receipt.infrastructure.parsing

import com.betgol.receipt.domain.ParsedReceipt
import com.betgol.receipt.domain.parsing.ReceiptParser
import zio.{IO, ULayer, ZIO, ZLayer}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

/**
 * Parses a raw SUNAT QR Code Data String into a `ParsedReceipt`.
 * The input format follows the "Cadena de Datos del Código QR" standard
 * defined by SUNAT for Peruvian Electronic Invoicing (CPE).
 *
 * Expected format (7 fields minimum), separated by `|`:
 *
 * issuerTaxId | docType | docSeries | docNumber | vatAmount | totalAmount | issueDate
 *
 * Field details:
 *   - issuerTaxId: 11-digit numeric string (e.g. "12345678901")
 *   - docType:     must be "01" or "03"
 *   - docSeries:   pattern `^[FB]\d{3}$ (e.g. "F001", "B123")`
 *   - docNumber:   8-digit numeric string
 *   - vatAmount:   ignored
 *   - totalAmount: decimal number; commas are allowed and normalized
 *   - issueDate:   accepts formats "dd/MM/yyyy" or "yyyy-MM-dd"
 *   - customerIdType: ignored
 *   - customerDocumentNumber: ignored
 *   - digitalSignatureHash: ignored
 *
 * Example rawData:
 * "12345678901|01|F001|00001234|18.0|150.50|2024-11-30"
 *
 * @param rawData pipe-separated receipt string
 * @return ParsedReceipt or an error describing why parsing failed
 */
case class SunatQrParser() extends ReceiptParser {

  private val allowedDateFormats = List(
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
  )

  override def parse(rawData: String): IO[String, ParsedReceipt] = {

    val result: Either[String, ParsedReceipt] = for {
      parts <- {
        val p = rawData.split("\\|")
        if (p.length < 7) Left("Insufficient data fields") else Right(p)
      }

      issuerTaxId = parts(0)
      docType = parts(1)
      docSeries = parts(2)
      docId = parts(3)
      totalStr = parts(5).replace(",", ".")
      dateStr = parts(6)

      _ <- Either.cond(issuerTaxId.matches("\\d{11}"), (), s"Invalid Issuer Tax Id (RUC): $issuerTaxId")
      _ <- Either.cond(docType == "01" || docType == "03", (), s"Invalid document type: $docType")
      _ <- Either.cond(docSeries.matches("^[FB]\\d{3}$"), (), s"Invalid document series: $docSeries")
      _ <- Either.cond(docId.matches("\\d{8}"), (), s"Invalid document number: $docId")
      
      total <- Try(totalStr.toDouble).toOption
        .filter(_ > 0)
        .toRight("Invalid total amount")

      date <- allowedDateFormats.view
        .map(fmt => Try(LocalDate.parse(dateStr, fmt)).toOption)
        .find(_.isDefined)
        .flatten
        .toRight(s"Invalid date format: $dateStr")

    } yield ParsedReceipt(issuerTaxId, docType, docSeries, docId, total, date)
    
    ZIO.fromEither(result)
  }
}

object SunatQrParser {
  val layer: ULayer[ReceiptParser] = ZLayer.succeed(SunatQrParser())
}
