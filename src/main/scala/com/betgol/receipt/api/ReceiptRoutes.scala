package com.betgol.receipt.api

import com.betgol.receipt.api.dto.{ApiErrorResponse, ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.*
import com.betgol.receipt.services.ReceiptService
import zio.*
import zio.http.*
import zio.json.*


object ReceiptRoutes {

  val routes: Routes[ReceiptService, Response] =
    Routes(
      Method.POST / "processReceipt" -> handler { (req: Request) =>
        req.body.asString
          .map(_.fromJson[ReceiptRequest]).flatMap {
            case Right(request) =>
              ZIO.serviceWithZIO[ReceiptService](_.process(request.toCommand))
                .map { res => ReceiptSubmissionResponse(res.id.value, res.status.toString, res.message) }
                .map { responseDto => Response.json(responseDto.toJson) }
                .catchAll(mapErrorToResponse)
            case Left(jsonError) =>
              ZIO.succeed(Response.json(ApiErrorResponse(s"Invalid JSON format: $jsonError").toJson).status(Status.BadRequest))
          }
          .catchAll { e =>
            ZIO.logError(s"Transport error: $e") *>
            ZIO.succeed(Response.status(Status.InternalServerError))
          }
      }
    )

  private def mapErrorToResponse(error: ReceiptSubmissionError): UIO[Response] = error match {
//TODO delete
//    case InvalidReceipt(msg, _) =>
//      ZIO.succeed(Response.json(ApiErrorResponse(s"Invalid receipt: $msg").toJson).status(Status.BadRequest))
    case DuplicateReceipt(msg, _) =>
      ZIO.succeed(Response.json(ApiErrorResponse(msg).toJson).status(Status.Conflict))
    case SystemError(_, _) =>
      ZIO.succeed(Response.json(ApiErrorResponse("Internal Server Error").toJson).status(Status.InternalServerError))
  }
}