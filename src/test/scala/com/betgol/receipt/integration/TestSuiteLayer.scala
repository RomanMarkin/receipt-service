package com.betgol.receipt.integration

import com.betgol.receipt.domain.repos.{BonusAssignmentRepository, ReceiptVerificationRepository}
import com.betgol.receipt.domain.services.{BonusService, IdGenerator, VerificationService}
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.MongoDatabase
import zio.*


object TestSuiteLayer {

  private type SuiteEnv = MongoDatabase & ReceiptService & Scope
  
  def make(verificationServiceLayer: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService],
           bonusServiceLayer: ZLayer[IdGenerator & BonusAssignmentRepository, Nothing, BonusService]
          ): ZLayer[SharedTestLayer.InfraEnv & Scope, Throwable, SuiteEnv] =
    
    ZLayer.makeSome[SharedTestLayer.InfraEnv & Scope, SuiteEnv](
      verificationServiceLayer,
      bonusServiceLayer,
      ReceiptServiceLive.layer)
}