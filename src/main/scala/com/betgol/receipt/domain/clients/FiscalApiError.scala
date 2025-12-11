package com.betgol.receipt.domain.clients

sealed abstract class FiscalApiError(msg: String, cause: Throwable = null) extends Exception(msg, cause)
case class FiscalApiSerializationError(msg: String, cause: Throwable = null) extends FiscalApiError(msg, cause)
case class FiscalApiNetworkError(msg: String, cause: Throwable) extends FiscalApiError(msg, cause)
case class FiscalApiDeserializationError(msg: String, cause: Throwable = null) extends FiscalApiError(msg, cause)