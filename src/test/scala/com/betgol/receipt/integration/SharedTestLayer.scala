package com.betgol.receipt.integration

import com.betgol.receipt.domain.clients.FiscalClientProvider
import com.betgol.receipt.domain.parsing.ReceiptParser
import com.betgol.receipt.domain.repo.{ReceiptRepository, ReceiptRetryRepository}
import com.betgol.receipt.fixtures.TestMongoLayer
import com.betgol.receipt.infrastructure.parsing.SunatQrParser
import com.betgol.receipt.infrastructure.repo.{MongoReceiptRepository, MongoReceiptRetryRepository}
import com.betgol.receipt.mocks.clients.MockFiscalClientProvider
import com.betgol.receipt.service.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.MongoDatabase
import zio.ZLayer

object SharedTestLayer {
  val layer: ZLayer[Any, Throwable, MongoDatabase & ReceiptRepository & ReceiptRetryRepository & ReceiptParser & FiscalClientProvider & ReceiptService] =
    TestMongoLayer.layer >+> (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    SunatQrParser.layer >+>
    MockFiscalClientProvider.happyPath >+>
    ReceiptServiceLive.layer

  val infrastructure: ZLayer[Any, Throwable, MongoDatabase & ReceiptRepository & ReceiptRetryRepository & ReceiptParser] =
    TestMongoLayer.layer >+>
    (MongoReceiptRepository.layer ++ MongoReceiptRetryRepository.layer) ++
    SunatQrParser.layer
}