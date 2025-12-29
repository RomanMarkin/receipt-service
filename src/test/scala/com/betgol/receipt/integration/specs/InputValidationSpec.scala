package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import zio.*
import zio.http.*
import zio.test.*


object InputValidationSpec extends BasicIntegrationSpec {

  private val successfulPath: ZLayer[Any, Throwable, ReceiptService & Scope] =
    ZLayer.make[ReceiptService & Scope](
      SharedTestLayer.infraLayer,
      MockVerificationService.validDocPath,
      MockBonusService.bonusAssignedPath,
      ReceiptServiceLive.layer,
      Scope.default
    )

  override def spec = suite("Input Validation")(
    test("Returns 400 for corrupted JSON") {
      val req = buildRequest("""{"Corrupted" "JSON"}""")
      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        body.contains("Invalid JSON")
      )
    },

    test("Returns 400 for invalid JSON passed (Unexpected JSON structure)") {
      val badJson = """{"wrongField": "someData"}"""
      val req = buildRequest(badJson)
      for {
        response <- ReceiptRoutes.routes.runZIO(req)
        bodyStr  <- response.body.asString
      } yield assertTrue(
        response.status == Status.BadRequest,
        bodyStr.contains("Invalid JSON")
      )
    }

  ).provideLayerShared(successfulPath.orDie)
}
