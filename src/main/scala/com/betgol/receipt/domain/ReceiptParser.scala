package com.betgol.receipt.domain

import zio._
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try


trait ReceiptParser {
  /**
   * Parses a raw receipt string into a `ParsedReceipt`.
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
   *   - vatAmount:   decimal number; commas are allowed and normalized (ignored)
   *   - totalAmount: decimal number; commas are allowed and normalized
   *   - issueDate:   accepts formats "dd/MM/yyyy" or "yyyy-MM-dd"
   *
   * Example rawData:
   * "12345678901|01|F001|00001234|18.0|150.50|2024-11-30"
   *
   * @param rawData pipe-separated receipt string
   * @return ParsedReceipt or an error describing why parsing failed
   */
  def parse(rawData: String): IO[String, ParsedReceipt]
}

case class ReceiptParserLive() extends ReceiptParser {

  private val allowedDateFormats = List(
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
  )

  override def parse(rawData: String): IO[String, ParsedReceipt] = ZIO.attempt {
    val parts = rawData.split("\\|")

    if (parts.length < 7) throw new Exception("Insufficient data fields")

    val issuerTaxId = parts(0)
    val docType     = parts(1)
    val docSeries   = parts(2)
    val docId       = parts(3)
    // field #4 (VAT) is ignored
    val totalStr    = parts(5).replace(",", ".")
    val dateStr     = parts(6)

    // Validation Rules
    if (!issuerTaxId.matches("\\d{11}")) throw new Exception(s"Invalid Issuer Tax Id (RUC): $issuerTaxId")
    if (docType != "01" && docType != "03") throw new Exception(s"Invalid document type: $docType")
    if (!docSeries.matches("^[FB]\\d{3}$")) throw new Exception(s"Invalid document series: $docSeries")
    if (!docId.matches("\\d{8}")) throw new Exception(s"Invalid document number: $docId")

    val total = Try(totalStr.toDouble).getOrElse(throw new Exception("Invalid total amount"))

    val date = allowedDateFormats.view.map(fmt => Try(LocalDate.parse(dateStr, fmt)))
      .find(_.isSuccess)
      .map(_.get)
      .getOrElse(throw new Exception(s"Invalid date format: $dateStr"))

    ParsedReceipt(issuerTaxId, docType, docSeries, docId, total, date)
  }.mapError(_.getMessage) // Java exception => ZIO error string
}

object ReceiptParserLive {
  val layer: ULayer[ReceiptParser] = ZLayer.succeed(ReceiptParserLive())
}