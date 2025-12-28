package com.betgol.receipt.domain.repos

import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.clients.BonusApiSession
import zio.IO


trait BonusApiSessionRepository {
  def getSession: IO[RepositoryError, Option[BonusApiSession]]
  def saveSession(session: BonusApiSession): IO[RepositoryError, Unit]
}
