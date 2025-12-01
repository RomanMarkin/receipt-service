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
   * orgId | docType | series | docId | vat | total | date
   *
   * Field details:
   *   - orgId:   11-digit numeric string (e.g. "12345678901")
   *   - docType: must be "01" or "03"
   *   - series:  pattern `^[FB]\d{3}$ (e.g. "F001", "B123")`
   *   - docId:   8-digit numeric string
   *   - vat:     decimal number; commas are allowed and normalized (ignored)
   *   - total:   decimal number; commas are allowed and normalized
   *   - date:    accepts formats "dd/MM/yyyy" or "yyyy-MM-dd"
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

  private val dateFormats = List(
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
  )

  override def parse(rawData: String): IO[String, ParsedReceipt] = ZIO.attempt {
    val parts = rawData.split("\\|")

    if (parts.length < 7) throw new Exception("Insufficient data fields")

    val orgId     = parts(0)
    val docType   = parts(1)
    val series    = parts(2)
    val docId     = parts(3)
    // field #4 (VAT) is ignored
    val totalStr  = parts(5).replace(",", ".")
    val dateStr   = parts(6)

    // Validation Rules
    if (!orgId.matches("\\d{11}")) throw new Exception(s"Invalid OrgID: $orgId")
    if (docType != "01" && docType != "03") throw new Exception(s"Invalid DocType: $docType")
    if (!series.matches("^[FB]\\d{3}$")) throw new Exception(s"Invalid Series: $series")
    if (!docId.matches("\\d{8}")) throw new Exception(s"Invalid DocID: $docId")

    val total = Try(totalStr.toDouble).getOrElse(throw new Exception("Invalid Total Amount"))

    // Try parsing date with multiple formats
    val date = dateFormats.view.map(fmt => Try(LocalDate.parse(dateStr, fmt)))
      .find(_.isSuccess)
      .map(_.get)
      .getOrElse(throw new Exception(s"Invalid Date format: $dateStr"))

    ParsedReceipt(orgId, docType, series, docId, total, date)
  }.mapError(_.getMessage) // Java exception => ZIO error string
}

object ReceiptParserLive {
  val layer: ULayer[ReceiptParser] = ZLayer.succeed(ReceiptParserLive())
}