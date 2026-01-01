package com.betgol.receipt.infrastructure.services

import com.betgol.receipt.domain.Ids.BonusCode
import zio.test.*
import zio.test.Assertion.*


object HardcodedBonusEvaluatorSpec extends ZIOSpecDefault {

  def spec = suite("HardcodedBonusEvaluator Logic")(

    suite("No Bonus Range (< 10.0)")(
      test("Returns None for small amounts") {
        check(Gen.double(0.0, 9.99)) { amount =>
          val result = HardcodedBonusEvaluator.evaluate(BigDecimal(amount))
          assert(result)(isNone)
        }
      },
      test("Returns None for zero") {
        assert(HardcodedBonusEvaluator.evaluate(BigDecimal(0)))(isNone)
      },
      test("Returns None for negative amounts") {
        check(Gen.double(-100.0, -0.01)) { amount =>
          val result = HardcodedBonusEvaluator.evaluate(BigDecimal(amount))
          assert(result)(isNone)
        }
      }
    ),

    suite("Low Tier Bonus Range (10.0 <= x <= 50.0)")(
      test("Returns '10_FREE_SPINS' for amounts in range") {
        check(Gen.double(10.01, 49.99)) { amount =>
          val result = HardcodedBonusEvaluator.evaluate(BigDecimal(amount))
          assertTrue(result.get == BonusCode("10_FREE_SPINS"))
        }
      },
      test("Returns '10_FREE_SPINS' inclusive boundary 10.0") {
        val result = HardcodedBonusEvaluator.evaluate(BigDecimal("10.00"))
        assertTrue(result.get == BonusCode("10_FREE_SPINS"))
      },
      test("Returns '10_FREE_SPINS' inclusive boundary 50.0") {
        val result = HardcodedBonusEvaluator.evaluate(BigDecimal("50.00"))
        assertTrue(result.get == BonusCode("10_FREE_SPINS"))
      }
    ),

    suite("High Tier Bonus Range (> 50.0)")(
      test("Returns '20_FREE_SPINS' for large amounts") {
        check(Gen.double(50.01, 10000.0)) { amount =>
          val result = HardcodedBonusEvaluator.evaluate(BigDecimal(amount))
          assertTrue(result.get == BonusCode("20_FREE_SPINS"))
        }
      },
      test("Returns '20_FREE_SPINS' immediately after 50.0") {
        val result = HardcodedBonusEvaluator.evaluate(BigDecimal("50.01"))
        assertTrue(result.get == BonusCode("20_FREE_SPINS"))
      }
    )
  )
}