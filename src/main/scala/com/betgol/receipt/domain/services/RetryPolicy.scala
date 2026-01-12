package com.betgol.receipt.domain.services

import zio.{Clock, UIO, ZIO, ZLayer}

import java.time.Instant


trait RetryPolicy {

  /**
   * @param actualStatus The resolved status of the current operation
   * @param retryTriggerStatus The specific status value that implies a retry is needed (e.g. RetryScheduled)
   * @param currentAttempt The attempt number just completed
   * */
  def nextRetryTimestamp[S](actualStatus: S,
                            retryTriggerStatus: S,
                            currentAttempt: Int): UIO[Option[Instant]]
}


case class RetryPolicyLive(config: RetryPolicyConfig) extends RetryPolicy {

  override def nextRetryTimestamp[S](actualStatus: S,
                                     retryTriggerStatus: S,
                                     currentAttempt: Int): UIO[Option[Instant]] =
    if (actualStatus == retryTriggerStatus) {
      for {
        now <- Clock.instant
      } yield Some(calculateNextRetry(currentAttempt, now))
    } else {
      ZIO.none
    }

  private def calculateNextRetry(currentAttemptNumber: Int, from: Instant): Instant = {
    val delaySeconds = config.baseDelaySeconds * Math.pow(2, currentAttemptNumber).toLong
    from.plusSeconds(delaySeconds)
  }
}

object RetryPolicyLive {
  val layer: ZLayer[RetryPolicyConfig, Nothing, RetryPolicy] =
    ZLayer.fromFunction(RetryPolicyLive.apply _)
}

case class RetryPolicyConfig(baseDelaySeconds: Long)
object RetryPolicyConfig {
  val layer = ZLayer.succeed(RetryPolicyConfig(30))
}