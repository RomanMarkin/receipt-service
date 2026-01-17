package com.betgol.receipt.api.dto

import com.betgol.receipt.domain.SubmitReceipt
import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId}
import zio.json.*


// Requests
case class ReceiptRequest(receiptData: String, playerId: String, countryCode: String)
object ReceiptRequest {
  implicit val codec: JsonCodec[ReceiptRequest] = DeriveJsonCodec.gen
  extension(r: ReceiptRequest)
    def toCommand: Either[String, SubmitReceipt] = {
      CountryCode.from(r.countryCode).map { validCountryCode =>
        SubmitReceipt(
          receiptData = r.receiptData,
          playerId = PlayerId(r.playerId),
          country = validCountryCode
        )
      }
    }
}

// Responses
sealed trait ApiResponse

case class ApiSuccessResponse(message: String) extends ApiResponse
object ApiSuccessResponse {
  implicit val encoder: JsonEncoder[ApiSuccessResponse] = DeriveJsonEncoder.gen
}

case class ReceiptSubmissionResponse(receiptSubmissionId: String,
                                     status: String,
                                     message: Option[String] = None) extends ApiResponse
object ReceiptSubmissionResponse {
  implicit val encoder: JsonEncoder[ReceiptSubmissionResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[ReceiptSubmissionResponse] = DeriveJsonDecoder.gen
}

case class ApiErrorResponse(code: String, message: String) extends ApiResponse
object ApiErrorResponse {
  implicit val encoder: JsonEncoder[ApiErrorResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[ApiErrorResponse] = DeriveJsonDecoder.gen
}

