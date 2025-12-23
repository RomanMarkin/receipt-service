package com.betgol.receipt.domain.parsers

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*


trait ReceiptParser {
  def parse(rawData: String): IO[String, FiscalDocument]
}