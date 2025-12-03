package com.betgol.receipt.api

import com.betgol.receipt.api.dto.{ApiErrorResponse, ApiSuccessResponse, ReceiptRequest}
import com.betgol.receipt.domain.{DuplicateReceipt, InvalidReceipt, ReceiptError, SystemError}
import com.betgol.receipt.service.ReceiptService
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
                .as(Response.json(ApiSuccessResponse("Receipt accepted").toJson))
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

  private def mapErrorToResponse(error: ReceiptError): UIO[Response] = error match {
    case InvalidReceipt(msg) =>
      ZIO.succeed(Response.json(ApiErrorResponse(s"Invalid receipt: $msg").toJson).status(Status.BadRequest))
    case DuplicateReceipt(msg) =>
      ZIO.succeed(Response.json(ApiErrorResponse(msg).toJson).status(Status.Conflict))
    case SystemError(_) =>
      ZIO.succeed(Response.json(ApiErrorResponse("Internal Server Error").toJson).status(Status.InternalServerError))
  }
}