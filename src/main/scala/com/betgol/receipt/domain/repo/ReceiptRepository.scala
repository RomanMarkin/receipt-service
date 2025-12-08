package com.betgol.receipt.domain.repo

import com.betgol.receipt.domain.Types.{PlayerId, ReceiptId}
import com.betgol.receipt.domain.{ParsedReceipt, ReceiptError, TaxAuthorityConfirmation}
import zio.IO


trait ReceiptRepository {
  def saveValid(playerId: PlayerId, rawData: String, parsed: ParsedReceipt): IO[ReceiptError, ReceiptId]
  def saveInvalid(playerId: PlayerId, rawData: String, errorReason: String): IO[ReceiptError, ReceiptId]
  def updateConfirmed(receiptId: ReceiptId, confirmation: TaxAuthorityConfirmation): IO[ReceiptError, Unit]
}
