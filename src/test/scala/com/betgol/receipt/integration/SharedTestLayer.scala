package com.betgol.receipt.integration

import com.betgol.receipt.fixtures.TestMongoLayer
import com.betgol.receipt.infrastructure.parsing.SunatQrParser
import com.betgol.receipt.infrastructure.repo.{MongoReceiptRepository, MongoReceiptRetryRepository}
import com.betgol.receipt.mocks.clients.MockFiscalClientProvider
import com.betgol.receipt.service.ReceiptServiceLive

object SharedTestLayer {
  val layer =
    TestMongoLayer.layer >+> (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    SunatQrParser.layer >+>
    MockFiscalClientProvider.layer >+>
    ReceiptServiceLive.layer
}