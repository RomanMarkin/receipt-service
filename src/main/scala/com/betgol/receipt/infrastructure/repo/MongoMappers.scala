package com.betgol.receipt.infrastructure.repo

import com.betgol.receipt.domain._
import com.betgol.receipt.domain.Types._
import org.mongodb.scala.bson.Document
import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date

object MongoMappers {

  extension (d: LocalDate) {
    private def toMongoDate: Date = Date.from(d.atStartOfDay(ZoneId.of("UTC")).toInstant)
  }

  extension (i: Instant) {
    private def toMongoDate: Date = Date.from(i)
  }

  extension (r: ParsedReceipt) {
    def toDocument: Document = Document(
      "country"     -> r.country.toStringValue,
      "issuerTaxId" -> r.issuerTaxId,
      "type"        -> r.docType,
      "series"      -> r.docSeries,
      "number"      -> r.docNumber,
      "date"        -> r.date.toMongoDate,
      "totalAmount" -> r.totalAmount
    )
  }

  extension (c: TaxAuthorityConfirmation) {
    def toDocument: Document = Document(
      "apiProvider"      -> c.apiProvider,
      "confirmationTime" -> c.confirmationTime.toMongoDate,
      "verificationId"   -> c.verificationId,
      "statusMessage"    -> c.statusMessage
    )
  }

  def createRequestMeta(playerId: PlayerId, rawData: String): Document = Document(
    "requestDate" -> Date.from(Instant.now()),
    "playerId"    -> playerId.toStringValue,
    "rawData"     -> rawData
  )
}