package com.betgol.receipt.domain

sealed trait ReceiptError {
  def msg: String
}
case class InvalidReceipt(msg: String) extends ReceiptError
case class DuplicateReceipt(msg: String) extends ReceiptError
case class FiscalRecordNotFound(msg: String) extends ReceiptError
case class SystemError(msg: String) extends ReceiptError
