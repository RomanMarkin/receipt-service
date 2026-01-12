package com.betgol.receipt.domain.services

import zio.*
import zio.test.*
import java.time.Instant


object RetryPolicySpec extends ZIOSpecDefault {

  private val configLayer = ZLayer.succeed(RetryPolicyConfig(baseDelaySeconds = 30))
  private val serviceLayer = configLayer >>> RetryPolicyLive.layer

  override def spec = suite("RetryPolicyLive")(

    test("returns None when actual status does not match retry trigger status") {
      val actual = "COMPLETED"
      val trigger = "RETRY_NEEDED"

      for {
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(actual, trigger, 1))
      } yield assertTrue(result.isEmpty)
    },

    test("returns Some timestamp when statuses match") {
      val status = "RETRY_NEEDED"

      for {
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(status, status, 1))
      } yield assertTrue(result.isDefined)
    },

    test("calculates correct exponential backoff for Attempt #1 (Base 30s)") {
      // Formula: 30 * 2^1 = 60 seconds
      val status = "RETRY"

      for {
        _      <- TestClock.setTime(Instant.EPOCH) // Freeze time at Epoch (1970-01-01T00:00:00Z)
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(status, status, 1))

        expectedTime = Instant.EPOCH.plusSeconds(60)
      } yield assertTrue(result.contains(expectedTime))
    },

    test("calculates correct exponential backoff for Attempt #2 (Base 30s)") {
      // Formula: 30 * 2^2 = 120 seconds
      val status = "RETRY"

      for {
        _      <- TestClock.setTime(Instant.EPOCH)
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(status, status, 2))

        expectedTime = Instant.EPOCH.plusSeconds(120)
      } yield assertTrue(result.contains(expectedTime))
    },

    test("calculates correct exponential backoff for Attempt #3 (Base 30s)") {
      // Formula: 30 * 2^3 = 240 seconds
      val status = "RETRY"

      for {
        _      <- TestClock.setTime(Instant.EPOCH)
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(status, status, 3))

        expectedTime = Instant.EPOCH.plusSeconds(240)
      } yield assertTrue(result.contains(expectedTime))
    },

    test("respects custom base delay configuration") {
      // Use a custom layer with Base = 10s
      // Formula: 10 * 2^1 = 20 seconds
      val customConfigLayer = ZLayer.succeed(RetryPolicyConfig(baseDelaySeconds = 10))
      val customServiceLayer = customConfigLayer >>> RetryPolicyLive.layer

      val status = "RETRY"

      (for {
        _      <- TestClock.setTime(Instant.EPOCH)
        result <- ZIO.serviceWithZIO[RetryPolicy](_.nextRetryTimestamp(status, status, 1))

        expectedTime = Instant.EPOCH.plusSeconds(20)
      } yield assertTrue(result.contains(expectedTime)))
        .provide(customServiceLayer)
    }

  ).provide(serviceLayer)
}