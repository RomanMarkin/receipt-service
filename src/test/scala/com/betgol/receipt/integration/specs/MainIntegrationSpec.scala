package com.betgol.receipt.integration.specs

import com.betgol.receipt.integration.specs.bonus.{BonusAssignmentRejectionSpec, BonusNetworkFailureSpec, BonusSuccessSpec, BonusSystemErrorSpec, BonusUnavailableSpec, BonusExhaustedSpec}
import com.betgol.receipt.integration.specs.validation.{DuplicateReceiptSpec, ReceiptFormatValidationSpec, RequestDecodingSpec, UnparsableReceiptSpec}
import com.betgol.receipt.integration.specs.verification.{ReceiptAnnulledSpec, ReceiptNotFoundSpec, VerificationClientErrorSpec, VerificationDeserializationErrorSpec, VerificationNetworkFailureSpec, VerificationSerializationErrorSpec, VerificationServerErrorSpec, VerificationExhaustedSpec}
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer, DbCleaner}
import zio.Scope
import zio.test.*
import zio.{Scope, ZLayer}


object MainIntegrationSpec extends BasicIntegrationSpec {

  private val layer = Scope.default >+> SharedTestLayer.infraLayer

  private val cleanDb = TestAspect.before(DbCleaner.clean.orDie)

  override def spec = suite("All Integration Tests")(
    
    // Parsing and validation
    RequestDecodingSpec.suiteSpec @@ cleanDb,
    ReceiptFormatValidationSpec.suiteSpec @@ cleanDb,
    UnparsableReceiptSpec.suiteSpec @@ cleanDb,
    DuplicateReceiptSpec.suiteSpec @@ cleanDb,

    // Receipt verification
    ReceiptNotFoundSpec.suiteSpec @@ cleanDb,
    ReceiptAnnulledSpec.suiteSpec @@ cleanDb,
    VerificationNetworkFailureSpec.suiteSpec @@ cleanDb,
    VerificationServerErrorSpec.suiteSpec @@ cleanDb,
    VerificationClientErrorSpec.suiteSpec @@ cleanDb,
    VerificationSerializationErrorSpec.suiteSpec @@ cleanDb,
    VerificationDeserializationErrorSpec.suiteSpec @@ cleanDb,
    VerificationExhaustedSpec.suiteSpec @@ cleanDb,

    // Bonus assignment
    BonusSuccessSpec.suiteSpec @@ cleanDb,
    BonusUnavailableSpec.suiteSpec @@ cleanDb,
    BonusAssignmentRejectionSpec.suiteSpec @@ cleanDb,
    BonusNetworkFailureSpec.suiteSpec @@ cleanDb,
    BonusSystemErrorSpec.suiteSpec @@ cleanDb,
    BonusExhaustedSpec.suiteSpec @@ cleanDb

  ).provideLayerShared(layer)
    @@ TestAspect.withLiveClock
    @@ TestAspect.sequential
    @@ TestAspect.samples(1)
    @@ TestAspect.shrinks(1)
    @@ TestAspect.retries(0)
}