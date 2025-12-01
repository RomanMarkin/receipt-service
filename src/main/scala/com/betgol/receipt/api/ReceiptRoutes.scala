package com.betgol.receipt.api

import com.betgol.receipt.domain.*
import com.betgol.receipt.repo.ReceiptRepo
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes.*
import zio.*
import zio.http.*
import zio.json.*


object ReceiptRoutes {

  val routes: Routes[ReceiptParser & ReceiptRepo, Response] =
    Routes(
      Method.POST / "processReceipt" -> handler { (req: Request) =>
        req.body.asString
          .map(_.fromJson[ReceiptRequest])
          .flatMap {
            case Left(jsonError) =>
              ZIO.succeed(
                Response.json(ApiErrorResponse(s"Invalid JSON format: $jsonError").toJson).
                  status(Status.BadRequest)
              )
            case Right(request) =>
              processReceiptLogic(request)
        }
        .catchAll{ e =>
          ZIO.logError(s"Unexpected error: $e") *>
          ZIO.succeed(Response.status(Status.InternalServerError))
        }
      }
    )

  private def processReceiptLogic(req: ReceiptRequest): ZIO[ReceiptParser & ReceiptRepo, Nothing, Response] = {
    for {
      parser <- ZIO.service[ReceiptParser]
      repo   <- ZIO.service[ReceiptRepo]

      parseResult <- parser.parse(req.receiptData).either

      response <- parseResult match {
        case Right(parsed) =>
          repo.saveValid(req.playerId, req.receiptData, parsed)
            .as(Response.json(ApiSuccessResponse("Receipt accepted").toJson))
            .catchSome {
              case e: MongoWriteException if e.getError.getCode == 11000 =>
                ZIO.logWarning(s"Duplicate receipt attempt by player ${req.playerId}: issuerTaxId = ${parsed.issuerTaxId}, docType = ${parsed.docType}, docSeries = ${parsed.docSeries}, docNumber = ${parsed.docNumber}") *>
                ZIO.succeed(Response.json(ApiErrorResponse("Receipt already processed").toJson)
                  .status(Status.Conflict)
              )
            }
            .catchAll { e =>
              ZIO.logError(s"Failed to save valid receipt for player ${req.playerId}: $e") *>
              ZIO.succeed(Response.json(ApiErrorResponse(s"Failed to save receipt: $e").toJson).status(Status.InternalServerError))
            }

        case Left(errorMsg) =>
          (ZIO.logError(s"Parsing failed for player ${req.playerId}: $errorMsg") <&>
           repo.saveInvalid(req.playerId, req.receiptData, errorMsg).ignore) *>
          ZIO.succeed(Response.json(ApiErrorResponse(s"Invalid receipt data: $errorMsg").toJson)
            .status(Status.BadRequest)
          )
      }
    } yield response
  }
}