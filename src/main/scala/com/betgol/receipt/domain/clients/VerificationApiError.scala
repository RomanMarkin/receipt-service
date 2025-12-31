package com.betgol.receipt.domain.clients


sealed abstract class VerificationApiError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
object VerificationApiError {
  case class SerializationError(msg: String, cause: Throwable = null) extends VerificationApiError(msg, cause)
  case class DeserializationError(msg: String, cause: Throwable = null) extends VerificationApiError(msg, cause)
  case class NetworkError(msg: String, cause: Throwable) extends VerificationApiError(msg, cause)
  case class ClientError(statusCode: Int, msg: String) extends VerificationApiError(s"HTTP $statusCode Client Error: $msg")
  case class ServerError(statusCode: Int, msg: String) extends VerificationApiError(s"HTTP $statusCode Server Error: $msg")
}