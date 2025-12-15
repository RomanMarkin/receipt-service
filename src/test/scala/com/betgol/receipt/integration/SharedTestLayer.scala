package com.betgol.receipt.integration

import com.betgol.receipt.domain.clients.{BettingApiClient, FiscalClientProvider}
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptSubmissionRepository, VerificationRetryRepository}
import com.betgol.receipt.domain.services.{BonusEvaluator, IdGenerator}
import com.betgol.receipt.fixtures.TestMongoLayer
import com.betgol.receipt.infrastructure.parsers.SunatQrParser
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoVerificationRetryRepository}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.mocks.clients.{MockBettingXmlApiClient, MockFiscalClientProvider}
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.MongoDatabase
import zio.ZLayer


object SharedTestLayer {
  val layer: ZLayer[Any, Throwable, MongoDatabase & ReceiptSubmissionRepository & VerificationRetryRepository & BonusAssignmentRepository & ReceiptParser & FiscalClientProvider & BettingApiClient & IdGenerator & BonusEvaluator & ReceiptService] =
    TestMongoLayer.layer >+> (MongoReceiptSubmissionRepository.layer ++ MongoVerificationRetryRepository.layer ++ MongoBonusAssignmentRepository.layer) ++
    SunatQrParser.layer >+>
    MockFiscalClientProvider.happyPath >+>
    MockBettingXmlApiClient.layer >+>
    (UuidV7IdGenerator.layer ++ HardcodedBonusEvaluator.layer) >+>
    ReceiptServiceLive.layer

  val infrastructure: ZLayer[Any, Throwable, MongoDatabase & ReceiptSubmissionRepository & VerificationRetryRepository & BonusAssignmentRepository & ReceiptParser & IdGenerator & BonusEvaluator] =
    TestMongoLayer.layer >+>
    (MongoReceiptSubmissionRepository.layer ++ MongoVerificationRetryRepository.layer ++ MongoBonusAssignmentRepository.layer) ++
    MockBettingXmlApiClient.layer >+>
    (UuidV7IdGenerator.layer ++ HardcodedBonusEvaluator.layer) >+>
    SunatQrParser.layer
}