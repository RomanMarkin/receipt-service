package com.betgol.receipt.mocks.services

import com.betgol.receipt.config.VerificationServiceConfig
import com.betgol.receipt.domain.clients.VerificationClientProvider
import com.betgol.receipt.domain.repos.ReceiptVerificationRepository
import com.betgol.receipt.domain.services.{IdGenerator, VerificationService, VerificationServiceLive}
import com.betgol.receipt.mocks.clients.MockVerificationClientProvider
import zio.ZLayer


object MockVerificationService {
  private val mockConfig = VerificationServiceConfig(clients = null) //MockVerificationClientProvider will provide mock clients, so we don't need client configs.

  val layer: ZLayer[IdGenerator & ReceiptVerificationRepository & VerificationClientProvider, Nothing, VerificationService] =
    ZLayer.succeed(mockConfig) >+>
      VerificationServiceLive.layer

  val validDocPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
     MockVerificationClientProvider.validDocPath >>>
      MockVerificationService.layer

  val docNotFoundPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.docNotFoundPath >>>
      MockVerificationService.layer

  val docAnnulledPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.docAnnulledPath >>>
      MockVerificationService.layer

  val networkErrorPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.networkErrorPath >>>
      MockVerificationService.layer
  
  val serverErrorPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.serverErrorPath >>>
      MockVerificationService.layer

  val clientErrorPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.clientErrorPath >>>
      MockVerificationService.layer

  val serializationErrorPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.serializationErrorPath >>>
      MockVerificationService.layer

  val deserializationErrorPath: ZLayer[IdGenerator & ReceiptVerificationRepository, Nothing, VerificationService] =
    MockVerificationClientProvider.deserializationErrorPath >>>
      MockVerificationService.layer    
}