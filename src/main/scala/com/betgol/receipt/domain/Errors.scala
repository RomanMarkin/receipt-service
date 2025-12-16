package com.betgol.receipt.domain

sealed abstract class ReceiptSubmissionError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
final case class DuplicateReceipt(msg: String, cause: Throwable = null) extends ReceiptSubmissionError(msg, cause)
final case class SystemError(msg: String, cause: Throwable = null) extends ReceiptSubmissionError(msg, cause)


sealed abstract class BonusAssignmentError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
//TODO implement errors
//case class FiscalApiSerializationError(msg: String, cause: Throwable = null) extends BonusAssignmentError(msg, cause)

