package com.betgol.receipt.domain.models

import java.time.Instant


case class ReceiptVerificationJobStats(startTime: Instant,
                                       processed: Int = 0,
                                       succeeded: Int = 0,
                                       failed: Int = 0,
                                       rejected: Int = 0,
                                       rescheduled: Int = 0) {
  override def toString: String =
    s"ReceiptVerificationJobStats(" +
      s"startTime=$startTime, " +
      s"processed=$processed, " +
      s"succeeded=$succeeded, " +
      s"failed=$failed, " +
      s"rejected=$rejected, " +
      s"rescheduled=$rescheduled" +
      ")"
}


