package com.betgol.receipt.infrastructure.clients.factiliza

import com.betgol.receipt.domain.FiscalDocument
import zio.*
import zio.json.*

import java.time.format.DateTimeFormatter


/** Factiliza documentation: https://docs.factiliza.com/api-consulta/endpoint/sunat-cpe */
case class FactilizaRequest(ruc_emisor: String,
                            codigo_tipo_documento: String,
                            serie_documento: String,
                            numero_documento: String,
                            fecha_emision: String,
                            total: String)

object FactilizaRequest {
  implicit val encoder: JsonEncoder[FactilizaRequest] = DeriveJsonEncoder.gen[FactilizaRequest]

  private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def from(r: FiscalDocument): FactilizaRequest =
    FactilizaRequest(
      ruc_emisor = r.issuerTaxId,
      codigo_tipo_documento = r.docType,
      serie_documento = r.series,
      numero_documento = r.number,
      fecha_emision = r.issuedAt.format(dateFormat),
      total = f"${r.totalAmount}%.2f"
    )
}

case class FactilizaResponseData(
  @jsonField("comprobante_estado_codigo") estadoCp: String,
  @jsonField("comprobante_estado_descripcion") descripcionCp: Option[String],
  @jsonField("empresa_estado_codigo") estadoRuc: Option[String],
  @jsonField("empresa_condicion_codigo") condicionRuc: Option[String]
)

case class FactilizaResponse(
  status: Int,                    // 200, 400, etc.
  message: Option[String],        // "Exito", "Bad Request"
  data: Option[FactilizaResponseData],
  success: Option[Boolean] = None // Only appears in the 400 error payload
)

object FactilizaResponse {
  implicit val dataDecoder: JsonDecoder[FactilizaResponseData] = DeriveJsonDecoder.gen[FactilizaResponseData]
  implicit val decoder: JsonDecoder[FactilizaResponse] = DeriveJsonDecoder.gen[FactilizaResponse]
  
  implicit val dataEncoder: JsonEncoder[FactilizaResponseData] = DeriveJsonEncoder.gen[FactilizaResponseData]
  implicit val encoder: JsonEncoder[FactilizaResponse] = DeriveJsonEncoder.gen[FactilizaResponse]  
}