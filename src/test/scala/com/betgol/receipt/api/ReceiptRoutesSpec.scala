package com.betgol.receipt.api

import com.betgol.receipt.api.dto.ApiErrorResponse
import com.betgol.receipt.domain.ReceiptSubmissionError
import com.betgol.receipt.domain.models.SubmissionStatus
import com.betgol.receipt.mocks.services.MockReceiptService
import zio.*
import zio.http.*
import zio.json.*
import zio.test.*


object ReceiptRoutesSpec extends ZIOSpecDefault {

  private def buildRequest(body: String): Request =
    Request.post(URL.root / "processReceipt", Body.fromString(body))

  private def getErrorBody(response: Response): ZIO[Any, String, ApiErrorResponse] =
    for {
      bodyStr <- response.body.asString
        .mapError(t => s"Failed to read body: ${t.getMessage}")
      error   <- ZIO.fromEither(bodyStr.fromJson[ApiErrorResponse])
        .mapError(e => s"Failed to parse error JSON: $e. Raw body: $bodyStr")
    } yield error

  override def spec = suite("ReceiptRoutes Error Handling")(

    test("Returns 400 Bad Request [InvalidJson] when body is malformed") {
      val req = buildRequest("{ broken_json: }")

      (for {
        response <- ReceiptRoutes.routes.runZIO(req)
        errorDto <- getErrorBody(response)
      } yield assertTrue(
        response.status == Status.BadRequest,
        errorDto.code == "InvalidJson",
        errorDto.message.contains("Invalid JSON format")
      )).provide(
        MockReceiptService.successPath(SubmissionStatus.Verified),
        Scope.default
      )
    },

    test("Returns 400 Bad Request [InvalidParameters] when domain validation fails") {
      val invalidJson = """{"receiptData":"xyz", "playerId":"", "countryCode":"UnexistingCountry"}"""
      val req = buildRequest(invalidJson)

      (for {
        response <- ReceiptRoutes.routes.runZIO(req)
        errorDto <- getErrorBody(response)
      } yield assertTrue(
        response.status == Status.BadRequest,
        errorDto.code == "InvalidParameters"
      )).provide(
        MockReceiptService.successPath(SubmissionStatus.Verified),
        Scope.default
      )
    },

    test("Returns 409 Conflict [DuplicateReceipt] when service returns duplicate error") {
      val validJson = """{"receiptData":"xyz", "playerId":"p1", "countryCode":"PE"}"""
      val req = buildRequest(validJson)

      val errorScenario = MockReceiptService.failure(
        ReceiptSubmissionError.DuplicateReceipt("Receipt already exists", null)
      )

      (for {
        response <- ReceiptRoutes.routes.runZIO(req)
        errorDto <- getErrorBody(response)
      } yield assertTrue(
        response.status == Status.Conflict,
        errorDto.code == "DuplicateReceipt",
        errorDto.message == "Receipt already exists"
      )).provide(errorScenario, Scope.default)
    },

    test("Returns 500 Internal Server Error [SystemError] when service returns system error") {
      val validJson = """{"receiptData":"xyz", "playerId":"p1", "countryCode":"PE"}"""
      val req = buildRequest(validJson)

      val errorScenario = MockReceiptService.failure(
        ReceiptSubmissionError.SystemError("Database connection lost", new RuntimeException("boom"))
      )

      (for {
        response <- ReceiptRoutes.routes.runZIO(req)
        errorDto <- getErrorBody(response)
      } yield assertTrue(
        response.status == Status.InternalServerError,
        errorDto.code == "SystemError",
        errorDto.message == "Internal Server Error"
      )).provide(errorScenario, Scope.default)
    },

    test("Returns 500 Internal Server Error [SystemError] when unexpected exception occurs") {
      val validJson = """{"receiptData":"xyz", "playerId":"p1", "countryCode":"PE"}"""
      val req = buildRequest(validJson)

      val defectScenario = MockReceiptService.defect(
        new NullPointerException("Unexpected bug in service logic")
      )

      (for {
        response <- ReceiptRoutes.routes.runZIO(req)
        errorDto <- getErrorBody(response)
      } yield assertTrue(
        response.status == Status.InternalServerError,
        errorDto.code == "SystemError",
        errorDto.message == "Internal Server Error"
      )).provide(defectScenario, Scope.default)
    }
  )
}