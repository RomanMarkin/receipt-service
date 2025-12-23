package com.betgol.receipt.domain

import com.betgol.receipt.domain.Ids.{CountryCode, PlayerId, SubmissionId}
import com.betgol.receipt.domain.models.SubmissionStatus


case class SubmitReceipt(receiptData: String,
                         playerId: PlayerId,
                         country: CountryCode)

final case class ReceiptSubmissionResult(id: SubmissionId,
                                         status: SubmissionStatus,
                                         message: Option[String] = None)