package com.betgol.receipt.domain.models

import com.betgol.receipt.domain.Ids.{BonusAssignmentId, BonusCode, PlayerId, SubmissionId}

import java.time.Instant


case class BonusAssignment(id: BonusAssignmentId,
                           submissionId: SubmissionId,
                           playerId: PlayerId,
                           bonusCode: BonusCode,
                           status: BonusAssignmentStatus,
                           nextRetryAt: Option[Instant],
                           attempts: List[BonusAssignmentAttempt],
                           createdAt: Instant,
                           updatedAt: Instant)

object BonusAssignment {
  def initial(id: BonusAssignmentId, submissionId: SubmissionId, playerId: PlayerId, code: BonusCode, now: Instant): BonusAssignment =
    BonusAssignment(
      id = id,
      submissionId = submissionId,
      playerId = playerId,
      bonusCode = code,
      status = BonusAssignmentStatus.Pending,
      nextRetryAt = None,
      attempts = List.empty,
      createdAt = now,
      updatedAt = now)
}

case class BonusAssignmentAttempt(status: BonusAssignmentAttemptStatus,
                                  attemptNumber: Int,
                                  attemptedAt: Instant,
                                  description: Option[String])

enum BonusAssignmentStatus {
  case NoBonus             // No bonus available, according to BonusEvaluator
  case Pending             // Created, not yet processed
  case RetryScheduled      // Last attempt failed, next attempt is expected
  case Assigned            // Bonus successfully assigned
  case Rejected            // The betting system rejected bonus assignment
  case Exhausted           // Bonus assignment failed N times. No more attempts planned
}

enum BonusAssignmentAttemptStatus {
  case Success
  case Rejected
  case SystemError
}