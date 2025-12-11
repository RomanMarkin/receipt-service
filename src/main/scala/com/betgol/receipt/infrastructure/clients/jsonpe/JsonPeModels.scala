package com.betgol.receipt.infrastructure.clients.jsonpe

import com.betgol.receipt.domain.ParsedReceipt
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** JsonPe documentation: https://docs.json.pe/api-consulta/endpoint/sunat-cpe */
case class JsonPeRequest(ruc_emisor: String,
                            codigo_tipo_documento: String,
                            serie_documento: String,
                            numero_documento: String,
                            fecha_de_emision: String,
                            total: String)

object JsonPeRequest {
  implicit val encoder: JsonEncoder[JsonPeRequest] = DeriveJsonEncoder.gen[JsonPeRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def from(r: ParsedReceipt): JsonPeRequest =
    JsonPeRequest(
      ruc_emisor = r.issuerTaxId,
      codigo_tipo_documento = r.docType,
      serie_documento = r.docSeries,
      numero_documento = r.docNumber,
      fecha_de_emision = r.date.format(dateFormat),
      total = String.format(java.util.Locale.US, "%.2f", Double.box(r.totalAmount))
    )
}

case class JsonPeResponseData(
                                  @jsonField("comprobante_estado_codigo") estadoCp: String,
                                  @jsonField("comprobante_estado_descripcion") descripcionCp: Option[String],
                                  @jsonField("empresa_estado_codigo") estadoRuc: Option[String],
                                  @jsonField("empresa_condicion_codigo") condicionRuc: Option[String]
                                )

case class JsonPeResponse(success: Boolean,
                          data: Option[JsonPeResponseData],
                          message: Option[String])

object JsonPeResponse {
  implicit val dataDecoder: JsonDecoder[JsonPeResponseData] = DeriveJsonDecoder.gen[JsonPeResponseData]
  implicit val decoder: JsonDecoder[JsonPeResponse] = DeriveJsonDecoder.gen[JsonPeResponse]

  implicit val dataEncoder: JsonEncoder[JsonPeResponseData] = DeriveJsonEncoder.gen[JsonPeResponseData]
  implicit val encoder: JsonEncoder[JsonPeResponse] = DeriveJsonEncoder.gen[JsonPeResponse]
}