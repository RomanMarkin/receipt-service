package com.betgol.receipt.api.dto

import com.betgol.receipt.domain.ProcessReceiptCommand
import com.betgol.receipt.domain.Types.PlayerId
import zio.json.*


// Requests
case class ReceiptRequest(receiptData: String, playerId: String)
object ReceiptRequest {
  implicit val codec: JsonCodec[ReceiptRequest] = DeriveJsonCodec.gen
  extension(r: ReceiptRequest)
    def toCommand: ProcessReceiptCommand = ProcessReceiptCommand(r.receiptData, PlayerId(r.playerId))
}

// Responses
sealed trait ApiResponse

case class ApiSuccessResponse(message: String) extends ApiResponse
object ApiSuccessResponse {
  implicit val encoder: JsonEncoder[ApiSuccessResponse] = DeriveJsonEncoder.gen
}

case class ApiErrorResponse(error: String) extends ApiResponse
object ApiErrorResponse {
  implicit val encoder: JsonEncoder[ApiErrorResponse] = DeriveJsonEncoder.gen
}

