package com.betgol.receipt.integration

import zio.ZIO
import zio.http.{Body, Path, Request, URL}
import zio.test.Gen

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try


trait TestHelpers {

  def buildRequest(body: String): Request =
    Request.post(URL(Path.root / "processReceipt"), Body.fromString(body))

  def makeReceiptData(issuerTaxId: String = "12345678901",
                      docType: String = "01",
                      docSeries: String = "F001",
                      docNumber: String = "00001234",
                      total: String = "100.00",
                      date: String = "2025-01-01"): String =
    s"$issuerTaxId|$docType|$docSeries|$docNumber|10.00|$total|$date|6|hash"

  def validReceiptDataGen: Gen[Any, (String, String, String, String, String, LocalDate, String)] = {
    for {
      idx <- Gen.fromZIO(ZIO.succeed(GlobalTestState.seriesCounter.getAndIncrement() % 1000)) // use modulo 1000 (% 1000) to ensure it stays within 3 digits (000-999)
      suffix = f"$idx%03d" // formats 1 -> "001"
      issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
      docType = "01"
      docSeries <- Gen.elements(s"F$suffix", s"B$suffix")
      docNumber <- Gen.stringN(8)(Gen.numericChar)
      total <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
      date <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
      pattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
      dateStr = date.format(DateTimeFormatter.ofPattern(pattern))
      receiptData = makeReceiptData(
        issuerTaxId = issuerTaxId,
        docType = docType,
        docSeries = docSeries,
        docNumber = docNumber,
        total = total,
        date = dateStr
      )
    } yield (issuerTaxId, docType, docSeries, docNumber, total, date, receiptData)
  }

  extension (str: String) {
    def isValidUuid: Boolean =
    Try(UUID.fromString(str)).isSuccess
  }
}