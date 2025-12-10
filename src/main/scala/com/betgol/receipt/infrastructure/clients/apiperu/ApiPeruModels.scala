package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.domain.ParsedReceipt
import com.betgol.receipt.domain.clients.FiscalApiSerializationError
import zio.*
import zio.json.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter


/** ApiPeru documentation: https://docs.apiperu.dev/enpoints/consulta-cpe */
case class ApiPeruRequest(ruc_emisor: String,
                          tipo_comprobante: String,
                          serie_comprobante: String,
                          numero_comprobante: String,
                          fecha_emision: String,
                          total_comprobante: Double)

object ApiPeruRequest {
  implicit val encoder: JsonEncoder[ApiPeruRequest] = DeriveJsonEncoder.gen[ApiPeruRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def from(r: ParsedReceipt): ApiPeruRequest =
    ApiPeruRequest(
      ruc_emisor = r.issuerTaxId,
      tipo_comprobante = r.docType,
      serie_comprobante = r.docSeries,
      numero_comprobante = r.docNumber,
      fecha_emision = r.date.format(dateFormat),
      total_comprobante = r.totalAmount
    )
}

case class ApiPeruResponseData(estado_cp: String,
                               estado_ruc: String,
                               condicion_ruc: String,
                               observaciones: Option[List[String]] = None)

case class ApiPeruResponse(success: Boolean,
                           data: Option[ApiPeruResponseData],
                           message: Option[String])

object ApiPeruResponse {
  implicit val dataDecoder: JsonDecoder[ApiPeruResponseData] = DeriveJsonDecoder.gen[ApiPeruResponseData]
  implicit val decoder: JsonDecoder[ApiPeruResponse] = DeriveJsonDecoder.gen[ApiPeruResponse]
}