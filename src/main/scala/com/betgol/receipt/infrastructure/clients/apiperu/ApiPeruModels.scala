package com.betgol.receipt.infrastructure.clients.apiperu

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** ApiPeru documentation: https://docs.apiperu.dev/enpoints/consulta-cpe */
case class ApiPeruRequest(@jsonField("ruc_emisor")            issuerTaxId: String,
                          @jsonField("codigo_tipo_documento") docTypeCode: String,
                          @jsonField("serie_documento")       docSeries: String,
                          @jsonField("numero_documento")      docNumber: String,
                          @jsonField("fecha_de_emision")      issueDate: String,
                          @jsonField("total")                 totalAmount: BigDecimal)

object ApiPeruRequest {
  implicit val encoder: JsonEncoder[ApiPeruRequest] = DeriveJsonEncoder.gen[ApiPeruRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def from(r: FiscalDocument): ApiPeruRequest =
    ApiPeruRequest(
      issuerTaxId = r.issuerTaxId,
      docTypeCode = r.docType,
      docSeries = r.series,
      docNumber = r.number,
      issueDate = r.issuedAt.format(dateFormat),
      totalAmount = r.totalAmount
    )
}

case class ApiPeruResponseData(@jsonField("comprobante_estado_codigo") receiptStatusCode: String,
                               @jsonField("comprobante_estado_descripcion") receiptStatusDescription: Option[String],
                               @jsonField("empresa_estado_codigo") companyStatusCode: Option[String],
                               @jsonField("empresa_condicion_codigo") companyConditionCode: Option[String])

case class ApiPeruResponse(success: Boolean,
                           data: Option[ApiPeruResponseData],
                           message: Option[String])

object ApiPeruResponse {
  implicit val dataDecoder: JsonDecoder[ApiPeruResponseData] = DeriveJsonDecoder.gen[ApiPeruResponseData]
  implicit val decoder: JsonDecoder[ApiPeruResponse] = DeriveJsonDecoder.gen[ApiPeruResponse]

  implicit val dataEncoder: JsonEncoder[ApiPeruResponseData] = DeriveJsonEncoder.gen[ApiPeruResponseData]
  implicit val encoder: JsonEncoder[ApiPeruResponse] = DeriveJsonEncoder.gen[ApiPeruResponse]
}