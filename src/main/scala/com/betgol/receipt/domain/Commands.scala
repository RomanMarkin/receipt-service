package com.betgol.receipt.domain

import com.betgol.receipt.domain.Ids.{PlayerId, SubmissionId}


case class SubmitReceipt(receiptData: String,
                         playerId: PlayerId)

final case class ReceiptSubmissionResult(id: SubmissionId,
                                         status: SubmissionStatus)