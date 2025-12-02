package com.betgol.receipt.domain

import zio.json._
import java.time.LocalDate


case class ReceiptRequest(receiptData: String, playerId: String)
object ReceiptRequest {
  implicit val codec: JsonCodec[ReceiptRequest] = DeriveJsonCodec.gen
}

sealed trait ApiResponse

case class ApiSuccessResponse(message: String) extends ApiResponse
object ApiSuccessResponse {
  implicit val encoder: JsonEncoder[ApiSuccessResponse] = DeriveJsonEncoder.gen
}

case class ApiErrorResponse(error: String) extends ApiResponse
object ApiErrorResponse {
  implicit val encoder: JsonEncoder[ApiErrorResponse] = DeriveJsonEncoder.gen
}

case class ParsedReceipt(issuerTaxId: String,
                         docType: String,
                         docSeries: String,
                         docNumber: String,
                         totalAmount: Double,
                         date: LocalDate)

enum ReceiptStatus {
  case ValidReceiptData, InvalidReceiptData
}
