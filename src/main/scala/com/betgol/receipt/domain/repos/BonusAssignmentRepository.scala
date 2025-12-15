package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.Ids.BonusAssignmentId
import com.betgol.receipt.domain.{BonusAssignment, BonusAssignmentError, BonusAssignmentStatus}
import zio.IO


trait BonusAssignmentRepository {
  def save(assignment: BonusAssignment): IO[BonusAssignmentError, Unit]
  def updateStatus(id: BonusAssignmentId, status: BonusAssignmentStatus, error: Option[String] = None): IO[BonusAssignmentError, Unit]
}
