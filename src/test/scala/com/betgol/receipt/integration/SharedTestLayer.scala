package com.betgol.receipt.integration

import com.betgol.receipt.domain.parsers.ReceiptParser
import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptSubmissionRepository, ReceiptVerificationRepository}
import com.betgol.receipt.domain.services.*
import com.betgol.receipt.fixtures.TestMongoLayer
import com.betgol.receipt.infrastructure.parsers.SunatQrParser
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.infrastructure.services.UuidV7IdGenerator
import org.mongodb.scala.MongoDatabase
import zio.{Scope, ZLayer}


object SharedTestLayer {

  type InfraEnv = MongoDatabase &
    ReceiptSubmissionRepository &
    ReceiptVerificationRepository &
    BonusAssignmentRepository &
    ReceiptParser &
    IdGenerator

  val infraLayer: ZLayer[Scope, Throwable, InfraEnv] =
    TestMongoLayer.layer >+>
    (MongoReceiptSubmissionRepository.layer ++ MongoReceiptVerificationRepository.layer ++ MongoBonusAssignmentRepository.layer) ++
    SunatQrParser.layer ++
    UuidV7IdGenerator.layer
}