package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.domain.ParsedReceipt
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** ApiPeru documentation: https://docs.apiperu.dev/enpoints/consulta-cpe */
case class ApiPeruRequest(ruc_emisor: String,
                          codigo_tipo_documento: String,
                          serie_documento: String,
                          numero_documento: String,
                          fecha_de_emision: String,
                          total: Double)

object ApiPeruRequest {
  implicit val encoder: JsonEncoder[ApiPeruRequest] = DeriveJsonEncoder.gen[ApiPeruRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def from(r: ParsedReceipt): ApiPeruRequest =
    ApiPeruRequest(
      ruc_emisor = r.issuerTaxId,
      codigo_tipo_documento = r.docType,
      serie_documento = r.docSeries,
      numero_documento = r.docNumber,
      fecha_de_emision = r.date.format(dateFormat),
      total = r.totalAmount
    )
}

case class ApiPeruResponseData(
  @jsonField("comprobante_estado_codigo") estadoCp: String,
  @jsonField("comprobante_estado_descripcion") descripcionCp: Option[String],
  @jsonField("empresa_estado_codigo") estadoRuc: Option[String],
  @jsonField("empresa_condicion_codigo") condicionRuc: Option[String]
)

case class ApiPeruResponse(success: Boolean,
                           data: Option[ApiPeruResponseData],
                           message: Option[String])

object ApiPeruResponse {
  implicit val dataDecoder: JsonDecoder[ApiPeruResponseData] = DeriveJsonDecoder.gen[ApiPeruResponseData]
  implicit val decoder: JsonDecoder[ApiPeruResponse] = DeriveJsonDecoder.gen[ApiPeruResponse]

  implicit val dataEncoder: JsonEncoder[ApiPeruResponseData] = DeriveJsonEncoder.gen[ApiPeruResponseData]
  implicit val encoder: JsonEncoder[ApiPeruResponse] = DeriveJsonEncoder.gen[ApiPeruResponse]
}