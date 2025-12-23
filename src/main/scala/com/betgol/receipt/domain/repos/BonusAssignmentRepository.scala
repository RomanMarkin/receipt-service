package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.models.{BonusAssignment, BonusAssignmentAttempt, BonusAssignmentStatus}
import zio.IO


trait BonusAssignmentRepository {
  def add(assignment: BonusAssignment): IO[RepositoryError, BonusAssignmentId]
  def addAttempt(id: BonusAssignmentId, attempt: BonusAssignmentAttempt, assignmentStatus: BonusAssignmentStatus): IO[RepositoryError, Unit]
}
