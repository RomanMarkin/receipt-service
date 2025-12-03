package com.betgol.receipt.domain

import com.betgol.receipt.domain.Types.CountryIsoCode

import java.time.{Instant, LocalDate}


case class ParsedReceipt(issuerTaxId: String,
                         docType: String,
                         docSeries: String,
                         docNumber: String,
                         totalAmount: Double,
                         date: LocalDate,
                         country: CountryIsoCode = CountryIsoCode("PE")) // Temporary hardcoded to Peru for now

enum ReceiptStatus {
  case ValidReceiptData
  case InvalidReceiptData
  case Verified
  case VerificationFailed
}

enum ReceiptRetryStatus {
  case Pending
}

case class TaxAuthorityConfirmation(apiProvider: String,
                                    confirmationTime: Instant,
                                    verificationId: String,
                                    statusMessage: String)
