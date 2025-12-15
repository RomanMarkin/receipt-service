package com.betgol.receipt.domain

import scala.annotation.targetName


object Ids {

  opaque type SubmissionId = String
  object SubmissionId {
    def apply(value: String): SubmissionId = value
  }
  extension (a: SubmissionId) {
    @targetName("submissionIdValue")
    def value: String = a
  }

  opaque type VerificationRetryId = String
  object VerificationRetryId {
    def apply(value: String): VerificationRetryId = value
  }
  extension (a: VerificationRetryId) {
    @targetName("verificationRetryIdValue")
    def value: String = a
  }

  opaque type PlayerId = String
  object PlayerId {
    def apply(value: String): PlayerId = value
  }
  extension (a: PlayerId) {
    @targetName("playerIdValue")
    def value: String = a
  }

  opaque type BonusAssignmentId = String
  object BonusAssignmentId {
    def apply(value: String): BonusAssignmentId = value
  }
  extension (a: BonusAssignmentId) {
    @targetName("bonusAssignmentIdValue")
    def value: String = a
  }

  opaque type CountryCode = String
  object CountryCode {
    def apply(value: String): CountryCode = value

    def from(value: String): Either[String, CountryCode] = {
      if (value.matches("^[A-Z]{2}$")) Right(value)
      else Left(s"Invalid ISO 3166 country code: $value")
    }
  }
  extension (a: CountryCode) {
    @targetName("countryCodeValue")
    def value: String = a
  }
}