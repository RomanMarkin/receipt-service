package com.betgol.receipt.domain.clients


sealed abstract class BonusApiError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
object BonusApiError {
  case class SessionError(msg: String) extends BonusApiError(msg)
  case class BonusRejected(msg: String) extends BonusApiError(msg)
  case class NetworkError(msg: String, cause: Throwable = null) extends BonusApiError(msg, cause)
  case class SystemError(msg: String, cause: Throwable = null) extends BonusApiError(msg, cause)
}
