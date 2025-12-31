package com.betgol.receipt.integration


object GlobalTestState {
  import java.util.concurrent.atomic.AtomicInteger
  val seriesCounter = new AtomicInteger(0)
}
