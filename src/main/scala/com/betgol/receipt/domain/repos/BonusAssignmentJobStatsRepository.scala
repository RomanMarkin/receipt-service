package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.BonusAssignmentJobStats
import zio.IO


trait BonusAssignmentJobStatsRepository {
  /** Persists the statistics of a job run. */
  def add(stats: BonusAssignmentJobStats): IO[RepositoryError, Unit]
}