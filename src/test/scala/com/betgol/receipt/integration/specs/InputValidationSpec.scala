package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import zio.*
import zio.http.*
import zio.test.*


object InputValidationSpec extends BasicIntegrationSpec {

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

  ).provideLayerShared(Scope.default >+> SharedTestLayer.successLayer.orDie)
}
