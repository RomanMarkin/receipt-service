package com.betgol.receipt.domain

import com.betgol.receipt.domain.Types.{CountryIsoCode, PlayerId, ReceiptId}

import java.time.{Instant, LocalDate, LocalDateTime}


case class ParsedReceipt(issuerTaxId: String,
                         docType: String,
                         docSeries: String,
                         docNumber: String,
                         totalAmount: Double,
                         date: LocalDate,
                         country: CountryIsoCode = CountryIsoCode("PE")) // Temporary hardcoded to Peru for now

enum ReceiptStatus {
  case InvalidReceiptData
  case VerificationPending
  case Verified
  case VerificationFailed
}

case class ReceiptRetry(receiptId: ReceiptId,
                        playerId: PlayerId,
                        attempts: Int,
                        addedAt: Instant,
                        country: CountryIsoCode,
                        status: ReceiptRetryStatus)

object ReceiptRetry {
  def apply(receiptId: ReceiptId,
            playerId: PlayerId,
            country: CountryIsoCode): ReceiptRetry =
    ReceiptRetry(receiptId = receiptId, playerId = playerId, attempts = 0, addedAt = Instant.now, country = country, status = ReceiptRetryStatus.Pending)
}

enum ReceiptRetryStatus {
  case Pending
}

case class TaxAuthorityConfirmation(apiProvider: String,
                                    confirmationTime: Instant,
                                    verificationId: String,
                                    statusMessage: String)
