package com.betgol.receipt.domain.parsing

import com.betgol.receipt.domain.ParsedReceipt
import zio.*


trait ReceiptParser {
  def parse(rawData: String): IO[String, ParsedReceipt]
}