package com.betgol.receipt.api

import com.betgol.receipt.api.dto.{ApiErrorResponse, ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.{ReceiptSubmissionError, ReceiptSubmissionResult}
import com.betgol.receipt.services.ReceiptService
import zio.*
import zio.http.*
import zio.json.*


object ReceiptRoutes {

  val routes: Routes[ReceiptService, Response] =
    Routes(
      Method.POST / "processReceipt" -> handler { (req: Request) =>
        val effect = for {
          bodyStr <- req.body.asString

          requestDto <- ZIO.fromEither(bodyStr.fromJson[ReceiptRequest])
            .mapError(err =>
              EarlyFailure(Status.BadRequest, "InvalidJson", s"Invalid JSON format: $err")
            )

          command <- ZIO.fromEither(requestDto.toCommand)
            .mapError(err =>
              EarlyFailure(Status.BadRequest, "InvalidParameters", err)
            )

          res <- ZIO.serviceWithZIO[ReceiptService](_.process(command))
        } yield res

        effect.map(toSuccessResponse)
          .catchAllCause { cause =>
            cause.failureOption match {
              case Some(e: ReceiptSubmissionError) => mapDomainError(e)
              case Some(e: EarlyFailure)           => errorResponse(e.code, e.message, e.status)
              case _ =>
                ZIO.logErrorCause("Unexpected system crash", cause) *>
                  errorResponse("SystemError", "Internal Server Error", Status.InternalServerError)
            }
          }
      },

      Method.GET / "health" -> handler(
        Response.text("OK").status(Status.Ok)
      )
    )

  private def toSuccessResponse(res: ReceiptSubmissionResult): Response =
    Response.json(
      ReceiptSubmissionResponse(res.id.value, res.status.toString, res.message).toJson
    )

  private def mapDomainError(error: ReceiptSubmissionError): UIO[Response] = {
    val (status, code, message) = error match {
      case ReceiptSubmissionError.DuplicateReceipt(msg, _) =>
        (Status.Conflict, "DuplicateReceipt", msg)
      case ReceiptSubmissionError.SystemError(_, _) =>
        (Status.InternalServerError, "SystemError", "Internal Server Error")
    }
    errorResponse(code, message, status)
  }

  private def errorResponse(code: String, message: String, status: Status): UIO[Response] =
    ZIO.succeed(
      Response.json(ApiErrorResponse(code, message).toJson).status(status)
    )

  private case class EarlyFailure(status: Status, code: String, message: String)
}