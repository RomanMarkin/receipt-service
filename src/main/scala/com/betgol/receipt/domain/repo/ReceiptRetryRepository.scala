package com.betgol.receipt.domain.repo

import com.betgol.receipt.domain.ReceiptError
import com.betgol.receipt.domain.Types.{CountryIsoCode, PlayerId, ReceiptId}
import zio.IO


trait ReceiptRetryRepository {
  def save(receiptId: ReceiptId, playerId: PlayerId, country: CountryIsoCode): IO[ReceiptError, Unit]
}