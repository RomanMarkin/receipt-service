package com.betgol.receipt.domain

import com.betgol.receipt.domain.Types.PlayerId

case class ProcessReceiptCommand(receiptData: String,
                                 playerId: PlayerId)