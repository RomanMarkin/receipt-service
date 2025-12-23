package com.betgol.receipt.integration

import com.betgol.receipt.domain.clients.VerificationClientProvider
import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptSubmissionRepository, ReceiptVerificationRepository}
import com.betgol.receipt.domain.services.*
import com.betgol.receipt.fixtures.TestMongoLayer
import com.betgol.receipt.infrastructure.parsers.SunatQrParser
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.infrastructure.services.{HardcodedBonusEvaluator, UuidV7IdGenerator}
import com.betgol.receipt.mocks.services.{MockBonusEvaluator, MockBonusService, MockVerificationService}
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.MongoDatabase
import zio.ZLayer


object SharedTestLayer {
  private val baseLayer: ZLayer[Any, Throwable,
    MongoDatabase & ReceiptSubmissionRepository & ReceiptVerificationRepository & BonusAssignmentRepository & ReceiptParser & IdGenerator] =

    TestMongoLayer.layer >+>
    (MongoReceiptSubmissionRepository.layer ++ MongoReceiptVerificationRepository.layer ++ MongoBonusAssignmentRepository.layer) ++
    SunatQrParser.layer >+>
    UuidV7IdGenerator.layer

  val withoutVerificationClientProvider: ZLayer[VerificationClientProvider, Throwable,
    MongoDatabase & ReceiptSubmissionRepository & ReceiptVerificationRepository & BonusAssignmentRepository & ReceiptParser & IdGenerator & BonusEvaluator & BonusService & VerificationService] =

    baseLayer >+>
    MockBonusEvaluator.bonusAvailablePath >+>
    (MockBonusService.bonusAssignedPath ++ MockVerificationService.layer) >+>
    MockVerificationService.layer

  val successLayer: ZLayer[Any, Throwable,
    MongoDatabase & ReceiptSubmissionRepository & ReceiptVerificationRepository & BonusAssignmentRepository & ReceiptParser & IdGenerator & BonusEvaluator & BonusService & VerificationService & ReceiptService] =

    baseLayer >+>
    MockBonusEvaluator.bonusAvailablePath >+>
    (MockBonusService.bonusAssignedPath ++ MockVerificationService.validDocPath) >+>
    ReceiptServiceLive.layer

}