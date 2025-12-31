package com.betgol.receipt.integration.specs

import com.betgol.receipt.integration.specs.bonus.{BonusAssignmentRejectionSpec, BonusNetworkFailureSpec, BonusSuccessSpec, BonusSystemErrorSpec, BonusUnavailableSpec}
import com.betgol.receipt.integration.specs.validation.{DuplicateReceiptSpec, ReceiptFormatValidationSpec, RequestDecodingSpec, UnparsableReceiptSpec}
import com.betgol.receipt.integration.specs.verification.{ReceiptAnnulledSpec, ReceiptNotFoundSpec, VerificationClientErrorSpec, VerificationDeserializationErrorSpec, VerificationNetworkFailureSpec, VerificationSerializationErrorSpec, VerificationSystemErrorSpec}
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import zio.Scope
import zio.test.*


object MainIntegrationSpec extends BasicIntegrationSpec {

  private val layer = Scope.default >+> SharedTestLayer.infraLayer

  override def spec = suite("All Integration Tests")(

    // Parsing and validation
    RequestDecodingSpec.suiteSpec,
    ReceiptFormatValidationSpec.suiteSpec,
    UnparsableReceiptSpec.suiteSpec,
    DuplicateReceiptSpec.suiteSpec,

    // Receipt verification
    ReceiptNotFoundSpec.suiteSpec,
    ReceiptAnnulledSpec.suiteSpec,
    VerificationNetworkFailureSpec.suiteSpec,
    VerificationSystemErrorSpec.suiteSpec,
    VerificationClientErrorSpec.suiteSpec,
    VerificationSerializationErrorSpec.suiteSpec,
    VerificationDeserializationErrorSpec.suiteSpec,

    // Bonus assignment
    BonusSuccessSpec.suiteSpec,
    BonusUnavailableSpec.suiteSpec,
    BonusAssignmentRejectionSpec.suiteSpec,
    BonusNetworkFailureSpec.suiteSpec,
    BonusSystemErrorSpec.suiteSpec

  ).provideLayerShared(layer)
    @@ TestAspect.withLiveClock
    @@ TestAspect.sequential
    @@ TestAspect.samples(1)
    @@ TestAspect.shrinks(1)
    @@ TestAspect.retries(0)
}