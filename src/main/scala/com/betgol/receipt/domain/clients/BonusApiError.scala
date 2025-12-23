package com.betgol.receipt.domain.clients


sealed abstract class BonusApiError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
object BonusApiError {
  case class BonusRejected(msg: String) extends BonusApiError(msg)
  case class SystemError(msg: String, cause: Throwable) extends BonusApiError(msg, cause)
}
