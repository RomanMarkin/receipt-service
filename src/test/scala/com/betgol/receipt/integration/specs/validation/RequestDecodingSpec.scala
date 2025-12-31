package com.betgol.receipt.integration.specs.validation

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import zio.http.*
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.test.*
import zio.{Scope, ZIO}


object RequestDecodingSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.validDocPath,
    MockBonusService.bonusAssignedPath,
  )

  val suiteSpec = suite("Transport Layer: JSON & Schema Validation")(
    test("Rejects syntactically invalid JSON with 400 BadRequest") {
      val req = buildRequest("""{"Corrupted" "JSON"}""")
      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        body     <- response.body.asString
        apiResponse  <- ZIO.fromEither(body.fromJson[ErrorApiResponse])
          .orElseFail(s"Response was not a valid Error JSON. Body: $body")
      } yield assertTrue(
        response.status == Status.BadRequest,
        apiResponse.error.contains("Invalid JSON format:")
      )
    },

    test("Rejects well-formed JSON that fails DTO schema decoding") {
      val badJson = """{"wrongField": "someData"}"""
      val req = buildRequest(badJson)
      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        body  <- response.body.asString
        apiResponse  <- ZIO.fromEither(body.fromJson[ErrorApiResponse])
          .orElseFail(s"Response was not a valid Error JSON. Body: $body")
      } yield assertTrue(
        response.status == Status.BadRequest,
        apiResponse.error.contains("Invalid JSON format:")
      )
    }

  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)

  case class ErrorApiResponse(error: String)
  object ErrorApiResponse {
    implicit val decoder: JsonDecoder[ErrorApiResponse] = DeriveJsonDecoder.gen[ErrorApiResponse]
  }
}
