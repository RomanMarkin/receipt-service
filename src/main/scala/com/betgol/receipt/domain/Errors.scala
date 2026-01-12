package com.betgol.receipt.domain


sealed abstract class ReceiptSubmissionError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
object ReceiptSubmissionError {
  final case class DuplicateReceipt(msg: String, cause: Throwable = null) extends ReceiptSubmissionError(msg, cause)
  final case class SystemError(msg: String, cause: Throwable = null) extends ReceiptSubmissionError(msg, cause)

  extension (e: RepositoryError) {
    def toSystemError: ReceiptSubmissionError = SystemError(e.getMessage, e)
  }
}

sealed abstract class RepositoryError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
object RepositoryError {
  final case class InsertError(msg: String, cause: Throwable = null) extends RepositoryError(msg, cause)
  final case class UpdateError(msg: String, cause: Throwable = null) extends RepositoryError(msg, cause)
  final case class FindError(msg: String, cause: Throwable = null) extends RepositoryError(msg, cause)
  final case class NotFound(msg: String) extends RepositoryError(msg)
  final case class Duplicate(msg: String, cause: Throwable) extends RepositoryError(msg, cause)
  final case class DataCorrupted(msg: String) extends RepositoryError(msg)
}