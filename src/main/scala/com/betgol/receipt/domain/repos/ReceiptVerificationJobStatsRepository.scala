package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.ReceiptVerificationJobStats
import zio.IO


trait ReceiptVerificationJobStatsRepository {
  /** Persists the statistics of a job run. */
  def add(stats: ReceiptVerificationJobStats): IO[RepositoryError, Unit]
}