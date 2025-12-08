package com.betgol.receipt.domain

object Types {

  opaque type ReceiptId = String
  object ReceiptId {
    def apply(value: String): ReceiptId = value
    extension (a: ReceiptId) def toStringValue: String = a
  }

  opaque type ReceiptRetryId = String
  object ReceiptRetryId {
    def apply(value: String): ReceiptRetryId = value
    extension (a: ReceiptRetryId) def toStringValue: String = a
  }

  opaque type PlayerId = String
  object PlayerId {
    def apply(value: String): PlayerId = value
    extension (a: PlayerId) def toStringValue: String = a
  }

  opaque type CountryIsoCode = String
  object CountryIsoCode {
    def apply(value: String): CountryIsoCode = value

    def from(value: String): Either[String, CountryIsoCode] = {
      if (value.matches("^[A-Z]{2}$")) Right(value)
      else Left(s"Invalid ISO Country Code: $value")
    }
    extension (a: CountryIsoCode) def toStringValue: String = a
  }
}