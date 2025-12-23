package com.betgol.receipt.services

import com.betgol.receipt.config.VerificationServiceConfig
import com.betgol.receipt.domain.Ids.*
import com.betgol.receipt.domain.RepositoryError
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.domain.repos.ReceiptVerificationRepository
import com.betgol.receipt.domain.services.{VerificationService, VerificationServiceLive}
import com.betgol.receipt.infrastructure.services.UuidV7IdGenerator
import com.betgol.receipt.services.VerificationClientsRaceSpec.test
import zio.*
import zio.test.*

import java.time.{Instant, LocalDate}


object VerificationClientsRaceSpec extends ZIOSpecDefault {

  case class DelayedMockClient(name: String, delay: Duration, result: VerificationResult) extends VerificationApiClient {
    override def providerName: String = name
    override def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult] = ZIO.sleep(delay).as(result)
  }

  case class FailingMockClient(name: String, delay: Duration, error: VerificationApiError) extends VerificationApiClient {
    override def providerName: String = name
    override def verify(receipt: FiscalDocument): IO[VerificationApiError, VerificationResult] = ZIO.sleep(delay) *> ZIO.fail(error)
  }

  case class MockVerificationClientProvider(clients: List[VerificationApiClient]) extends VerificationClientProvider {
    def getClientsFor(countryIso: CountryCode): UIO[List[VerificationApiClient]] = ZIO.succeed(clients)
  }

  val mockVerificationRepository = new ReceiptVerificationRepository() {
    override def add(vr: ReceiptVerification): IO[RepositoryError, VerificationId] = ZIO.succeed(VerificationId("1"))
    override def addAttempt(id: VerificationId, attempt: ReceiptVerificationAttempt, verificationStatus: ReceiptVerificationStatus): IO[RepositoryError, Unit] = ZIO.unit
  }

  val dummyReceiptZio = Clock.currentDateTime.map(now => FiscalDocument("123", "01", "F001", "1", 10.0, now.toLocalDate))

  val mockConfig = VerificationServiceConfig(verificationTimeoutSeconds = 4, maxRetries = 2, clients = null)

  def buildService(clients: List[VerificationApiClient]): ZLayer[Any, Nothing, VerificationService] = {
    val dependencies =
      ZLayer.succeed(mockConfig) ++
      UuidV7IdGenerator.layer ++
      ZLayer.succeed(mockVerificationRepository) ++
      ZLayer.succeed(MockVerificationClientProvider(clients))
    dependencies >>> ZLayer.fromFunction(VerificationServiceLive.apply _)
  }

  val verificationId = VerificationId("id")
  val submissionId = SubmissionId("id")
  val country = CountryCode("PE")

  override def spec = suite("ReceiptService Racing Logic")(

    test("Scenario 1: Fastest client wins") {
      val fixedTime = Instant.EPOCH
      val fastResult = VerificationResult(status = VerificationResultStatus.Valid, description = Some("Valid"))
      val slowResult = VerificationResult(status = VerificationResultStatus.NotFound, description = Some("NotFound"))

      val clientFast = DelayedMockClient("Fast", 1.second, fastResult)
      val clientSlow = DelayedMockClient("Slow", 5.seconds, slowResult)

      (for {
        dummyReceipt <- dummyReceiptZio
        fiber <- ZIO.serviceWithZIO[VerificationService](_.executeVerificationAttempt(verificationId, submissionId, country, dummyReceipt, currentAttempt = 1)).fork
        _ <- TestClock.adjust(2.second) // advance time between the completion of Fast and Slow
        verificationOutcome <- fiber.join
      } yield assertTrue(
        verificationOutcome.apiProvider.exists(_.contains("Fast")),
        verificationOutcome.status eq ReceiptVerificationStatus.Confirmed,
        verificationOutcome.statusDescription.exists(_.contains("Valid"))
      )).provide(buildService(List(clientFast, clientSlow)))
    },

    test("Scenario 2: If first client fails, second client wins") {
      val fixedTime = Instant.EPOCH
      val successResult = VerificationResult(status = VerificationResultStatus.Valid, description = Some("Valid"))

      val clientFastFail = FailingMockClient("FastFail", 100.millis, VerificationApiError.ClientError(402, "Test client error"))
      val clientSlowSuccess = DelayedMockClient("SlowSuccess", 2.seconds, successResult)

      (for {
        dummyReceipt <- dummyReceiptZio
        fiber <- ZIO.serviceWithZIO[VerificationService](_.executeVerificationAttempt(verificationId, submissionId, country, dummyReceipt, currentAttempt = 1)).fork
        _ <- TestClock.adjust(2.second) // advance time after the completion of SlowSuccess
        verificationOutcome <- fiber.join
      } yield assertTrue(
        verificationOutcome.apiProvider.exists(_.contains("SlowSuccess")),
        verificationOutcome.status eq ReceiptVerificationStatus.Confirmed,
        verificationOutcome.statusDescription.exists(_.contains("Valid"))
      )).provide(buildService(List(clientFastFail, clientSlowSuccess)))
    },

    test("Scenario 3: All clients are too slow (total timeout)") {
      val fixedTime = Instant.EPOCH
      val successResult1 = VerificationResult(status = VerificationResultStatus.Valid, description = Some("Valid"))
      val successResult2 = VerificationResult(status = VerificationResultStatus.Valid, description = Some("Valid"))

      val clientTooSlow1 = DelayedMockClient("Slow1", 10.seconds, successResult1)
      val clientTooSlow2 = DelayedMockClient("Slow2", 10.seconds, successResult2)

      (for {
        dummyReceipt <- dummyReceiptZio
        fiber <- ZIO.serviceWithZIO[VerificationService](_.executeVerificationAttempt(verificationId, submissionId, country, dummyReceipt, currentAttempt = 1)).fork
        _ <- TestClock.adjust(5.second)
        result <- fiber.join
      } yield assertTrue(
        result.apiProvider.isEmpty,
        result.status eq ReceiptVerificationStatus.RetryScheduled,
        result.statusDescription.exists(_.contains("Verification timed out (no provider responded in time)"))
      )).provide(buildService(List(clientTooSlow1, clientTooSlow2)))
    },

    test("Scenario 4: All clients are failed. Return the outcome of the last one") {
      val fixedTime = Instant.EPOCH

      val clientFastFail = FailingMockClient("FastFail", 0.seconds, VerificationApiError.ClientError(402, "Test client error"))
      val clientSlowFail = FailingMockClient("SlowFail", 2.seconds, VerificationApiError.ServerError(500, "Test server error"))

      (for {
        dummyReceipt <- dummyReceiptZio
        fiber <- ZIO.serviceWithZIO[VerificationService](_.executeVerificationAttempt(verificationId, submissionId, country, dummyReceipt, currentAttempt = 1)).fork
        _ <- TestClock.adjust(1.second) // the FastFail client now is failed
        _ <- TestClock.adjust(4.second) // the SlowFail client now is failed
        verificationOutcome <- fiber.join
      } yield assertTrue(
        verificationOutcome.apiProvider.isEmpty,
        verificationOutcome.status eq ReceiptVerificationStatus.RetryScheduled,
        verificationOutcome.statusDescription.exists(_.contains("All providers failed. Last error: Server Error (500) - Test server error"))
      )).provide(buildService(List(clientFastFail, clientSlowFail)))
    }
  )
}