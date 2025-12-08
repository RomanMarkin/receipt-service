package com.betgol.receipt.domain.repo

import com.betgol.receipt.domain.Types.ReceiptRetryId
import com.betgol.receipt.domain.{ReceiptError, ReceiptRetry}
import zio.IO


trait ReceiptRetryRepository {
  def save(rr: ReceiptRetry): IO[ReceiptError, ReceiptRetryId]
}