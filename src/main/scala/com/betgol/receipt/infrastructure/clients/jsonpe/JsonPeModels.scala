package com.betgol.receipt.infrastructure.clients.jsonpe

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** JsonPe documentation: https://docs.json.pe/api-consulta/endpoint/sunat-cpe */
case class JsonPeRequest(@jsonField("ruc_emisor")            issuerTaxId: String,
                         @jsonField("codigo_tipo_documento") docTypeCode: String,
                         @jsonField("serie_documento")       docSeries: String,
                         @jsonField("numero_documento")      docNumber: String,
                         @jsonField("fecha_de_emision")      issueDate: String,
                         @jsonField("total")                 totalAmount: String)

object JsonPeRequest {
  implicit val encoder: JsonEncoder[JsonPeRequest] = DeriveJsonEncoder.gen[JsonPeRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def from(r: FiscalDocument): JsonPeRequest =
    JsonPeRequest(
      issuerTaxId = r.issuerTaxId,
      docTypeCode = r.docType,
      docSeries = r.series,
      docNumber = r.number,
      issueDate = r.issuedAt.format(dateFormat),
      totalAmount = f"${r.totalAmount}%.2f"
    )
}

case class JsonPeResponseData(@jsonField("comprobante_estado_codigo") receiptStatusCode: String,
                              @jsonField("comprobante_estado_descripcion") receiptStatusDescription: Option[String],
                              @jsonField("empresa_estado_codigo") companyStatusCode: Option[String],
                              @jsonField("empresa_condicion_codigo") companyConditionCode: Option[String])

case class JsonPeResponse(success: Boolean,
                          data: Option[JsonPeResponseData],
                          message: Option[String])

object JsonPeResponse {
  implicit val dataDecoder: JsonDecoder[JsonPeResponseData] = DeriveJsonDecoder.gen[JsonPeResponseData]
  implicit val decoder: JsonDecoder[JsonPeResponse] = DeriveJsonDecoder.gen[JsonPeResponse]

  implicit val dataEncoder: JsonEncoder[JsonPeResponseData] = DeriveJsonEncoder.gen[JsonPeResponseData]
  implicit val encoder: JsonEncoder[JsonPeResponse] = DeriveJsonEncoder.gen[JsonPeResponse]
}