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

  opaque type VerificationId = String
  object VerificationId {
    def apply(value: String): VerificationId = value
  }
  extension (a: VerificationId) {
    @targetName("verificationIdValue")
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

  opaque type BonusCode = String
  object BonusCode {
    def apply(value: String): BonusCode = value
  }
  extension (a: BonusCode) {
    @targetName("bonusCodeValue")
    def value: String = a
  }

  opaque type BonusApiSessionCode = String
  object BonusApiSessionCode {
    def apply(a: String): BonusApiSessionCode = a
  }
  extension (a: BonusApiSessionCode) {
    @targetName("bonusApiSessionCodeValue")
    def value: String = a
  }
}