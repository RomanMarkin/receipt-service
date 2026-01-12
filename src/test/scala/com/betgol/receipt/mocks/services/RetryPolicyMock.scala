package com.betgol.receipt.mocks.services

import com.betgol.receipt.domain.services.RetryPolicy
import zio.{Clock, UIO, ZIO, ZLayer}

import java.time.Instant


object RetryPolicyMock {
  val layer: ZLayer[Any, Nothing, RetryPolicy] = ZLayer.succeed(
    new RetryPolicy {
      override def nextRetryTimestamp[S](actualStatus: S,
                                         retryTriggerStatus: S,
                                         currentAttempt: Int): UIO[Option[Instant]] =
        if (actualStatus == retryTriggerStatus) {
          Clock.instant.map(now => Some(now.plusMillis(10)))
        } else {
          ZIO.none
        }
    }
  )
}
