package com.betgol.receipt.integration

import zio.http.{Body, Path, Request, URL}

import java.util.UUID
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

  extension (str: String) {
    def isValidUuid: Boolean =
    Try(UUID.fromString(str)).isSuccess
  }
}