package com.betgol.receipt.domain

object Types {

  opaque type ReceiptId = String
  object ReceiptId {
    def apply(value: String): ReceiptId = value
    extension (a: ReceiptId) def toStringValue: String = a
  }

  opaque type PlayerId = String
  object PlayerId {
    def apply(value: String): PlayerId = value
    extension (a: PlayerId) def toStringValue: String = a
  }

  opaque type CountryIsoCode = String
  object CountryIsoCode {
    def apply(value: String): CountryIsoCode = value
    extension (a: CountryIsoCode) def toStringValue: String = a
  }
}