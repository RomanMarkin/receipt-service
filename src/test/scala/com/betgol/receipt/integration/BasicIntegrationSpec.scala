package com.betgol.receipt.integration

import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.test.*


trait BasicIntegrationSpec extends ZIOSpecDefault {

  // Setup config provider for reading test application.conf
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())


  def buildRequest(body: String): Request =
    Request.post(URL(Path.root / "processReceipt"), Body.fromString(body))

  def makeReceiptData(issuerTaxId: String = "12345678901",
                      docType: String = "01",
                      docSeries: String = "F001",
                      docNumber: String = "00001234",
                      total: String = "100.00",
                      date: String = "2025-01-01"): String =
    s"$issuerTaxId|$docType|$docSeries|$docNumber|10.00|$total|$date|6|hash"
}