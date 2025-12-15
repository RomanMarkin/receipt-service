package com.betgol.receipt.infrastructure.repos.mongo.mappers

import org.mongodb.scala.bson.{BsonDateTime, BsonDecimal128, BsonDocument, BsonInt32, BsonObjectId, BsonString, BsonValue, Decimal128}

import java.time.{Instant, LocalDate, ZoneId}

/** We use UUID v7 (36 chars) which is too long for native Mongo ObjectId (24 hex chars).
 * Therefore, IDs are stored as Strings, and these standard ObjectId helpers are not used.
 * */
object MongoPrimitives {

  extension (s: String) {
    def toBsonString: BsonString = BsonString(s)
    //def toBsonObjectId: BsonObjectId = BsonObjectId(s) We use UUID-7 which is not fit to Mongo ObjectId type
  }

  extension (i: Int) {
    def toBsonInt32: BsonInt32 = BsonInt32(i)
  }

  extension (bd: BigDecimal) {
    def toBsonDecimal128: BsonDecimal128 =
      new BsonDecimal128(new Decimal128(bd.bigDecimal))
  }

  extension (d: LocalDate) {
    def toBsonDateTime: BsonDateTime = BsonDateTime(d.atStartOfDay(ZoneId.of("UTC")).toInstant.toEpochMilli)
  }

  extension (i: Instant) {
    def toBsonDateTime: BsonDateTime = BsonDateTime(i.toEpochMilli)
  }

  extension (doc: BsonDocument) {
    def getSafe(key: String): Option[BsonValue] =
      Option(doc.get(key))

//    def getObjectIdOpt(key: String): Option[String] =
//      getSafe(key)
//        .filter(_.isObjectId)
//        .map(_.asObjectId().getValue.toHexString)

    def getStringOpt(key: String): Option[String] =
      getSafe(key)
        .filter(_.isString)
        .map(_.asString().getValue)

    def getDocOpt(key: String): Option[BsonDocument] =
      getSafe(key)
        .filter(_.isDocument)
        .map(_.asDocument())

    def getInstantOpt(key: String): Option[Instant] =
      getSafe(key)
        .filter(_.isDateTime)
        .map(_.asDateTime().getValue)
        .map(Instant.ofEpochMilli)

    def getLocalDateOpt(key: String): Option[LocalDate] =
      getInstantOpt(key)
        .map(_.atZone(ZoneId.of("UTC")).toLocalDate)

    def getBigDecimalOpt(key: String): Option[BigDecimal] =
      getSafe(key)
        .filter(_.isDecimal128)
        .map(v => BigDecimal(v.asDecimal128().getValue.bigDecimalValue()))

    def getIntOpt(key: String): Option[Int] =
      getSafe(key)
        .filter(_.isInt32)
        .map(_.asInt32().getValue)
  }
}
