package com.betgol.receipt.infrastructure.clients.factiliza

import com.betgol.receipt.domain.models.FiscalDocument
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** Factiliza documentation: https://docs.factiliza.com/api-consulta/endpoint/sunat-cpe */
case class FactilizaRequest(@jsonField("ruc_emisor")            issuerTaxId: String,
                            @jsonField("codigo_tipo_documento") docTypeCode: String,
                            @jsonField("serie_documento")       docSeries: String,
                            @jsonField("numero_documento")      docNumber: String,
                            @jsonField("fecha_emision")         issueDate: String,
                            @jsonField("total")                 totalAmount: String)

object FactilizaRequest {
  implicit val encoder: JsonEncoder[FactilizaRequest] = DeriveJsonEncoder.gen[FactilizaRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def from(r: FiscalDocument): FactilizaRequest =
    FactilizaRequest(
      issuerTaxId = r.issuerTaxId,
      docTypeCode = r.docType,
      docSeries = r.series,
      docNumber = r.number,
      issueDate = r.issuedAt.format(dateFormat),
      totalAmount = f"${r.totalAmount}%.2f"
    )
}

case class FactilizaResponseData(@jsonField("comprobante_estado_codigo") receiptStatusCode: String,
                                 @jsonField("comprobante_estado_descripcion") receiptStatusDescription: Option[String],
                                 @jsonField("empresa_estado_codigo") companyStatusCode: Option[String],
                                 @jsonField("empresa_condicion_codigo") companyConditionCode: Option[String])

case class FactilizaResponse(
  status: Int,
  message: Option[String],
  data: Option[FactilizaResponseData],
  success: Option[Boolean] = None // Only appears in the 400 error payload
)

object FactilizaResponse {
  implicit val dataDecoder: JsonDecoder[FactilizaResponseData] = DeriveJsonDecoder.gen[FactilizaResponseData]
  implicit val decoder: JsonDecoder[FactilizaResponse] = DeriveJsonDecoder.gen[FactilizaResponse]
  
  implicit val dataEncoder: JsonEncoder[FactilizaResponseData] = DeriveJsonEncoder.gen[FactilizaResponseData]
  implicit val encoder: JsonEncoder[FactilizaResponse] = DeriveJsonEncoder.gen[FactilizaResponse]  
}