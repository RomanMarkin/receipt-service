package com.betgol.receipt.integration.specs.validation

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.ReceiptRequest
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import zio.http.*
import zio.json.*
import zio.test.*
import zio.{Scope, ZIO}


object DuplicateReceiptSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.validDocPath,
    MockBonusService.bonusAssignedPath
  )

  val suiteSpec = suite("Data Integrity: Unique Receipt Constraint")(

    test("Enforces unique constraint on Fiscal Key (TaxId + Type + Series + Number) returning 409 Conflict") {
      val uniqueNumberGen = Gen.stringN(8)(Gen.numericChar)

      check(uniqueNumberGen) { uniqueNumber =>
        val playerId = "player-id"
        val country = "PE"
        val receiptData = makeReceiptData(docNumber = uniqueNumber)
        val req = buildRequest(ReceiptRequest(receiptData, playerId, country).toJson)

        for {
          resp1 <- ReceiptRoutes.routes.runZIO(req)
          resp2 <- ReceiptRoutes.routes.runZIO(req) // Duplicate submission
          body2 <- resp2.body.asString
          apiResponse2  <- ZIO.fromEither(body2.fromJson[ErrorApiResponse])
            .orElseFail(s"Response was not a valid Error JSON. Body: $body2")
        } yield assertTrue(
          resp1.status == Status.Ok,
          resp2.status == Status.Conflict,
          apiResponse2.error.contains("Receipt already exists")
        )
      }
    }

  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)

  case class ErrorApiResponse(error: String)
  object ErrorApiResponse {
    implicit val decoder: JsonDecoder[ErrorApiResponse] = DeriveJsonDecoder.gen[ErrorApiResponse]
  }

}