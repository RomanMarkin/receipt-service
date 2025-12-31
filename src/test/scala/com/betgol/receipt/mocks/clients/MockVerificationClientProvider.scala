package com.betgol.receipt.mocks.clients

import com.betgol.receipt.domain.Ids.CountryCode
import com.betgol.receipt.domain.clients.*
import zio.*


case class MockVerificationClientProvider(outcome: Either[VerificationApiError, VerificationResult]) extends VerificationClientProvider {

  override def getClientsFor(countryIso: CountryCode): UIO[List[VerificationApiClient]] =
      ZIO.succeed(List(
        MockVerificationApiClient(providerName = "MockProvider-Fast", outcome, delay = 0.second),
        MockVerificationApiClient(providerName = "MockProvider-Slow", outcome, delay = 1.second)
      )
    )
}

object MockVerificationClientProvider {
  val validDocPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Right(VerificationResult(status = VerificationResultStatus.Valid, description = Some("Mock Valid"), externalId = Some("mocked-external-id")))
      )
    )

  val docNotFoundPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Right(VerificationResult(status = VerificationResultStatus.NotFound, description = Some("Mock Not Found"), externalId = None))
      )
    )

  val docAnnulledPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Right(VerificationResult(status = VerificationResultStatus.Annulled, description = Some("Mock Annulled"), externalId = Some("mocked-external-id")))
      )
    )

  val networkErrorPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Left(VerificationApiError.NetworkError("Mock Network Error", new Exception("Mock Network Error")))
      )
    )

  val serverErrorPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Left(VerificationApiError.ServerError(statusCode = 500, "Mock Server Error"))
      )
    )

  val clientErrorPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Left(VerificationApiError.ClientError(statusCode = 400, "Mock Client Error"))
      )
    )

  val serializationErrorPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Left(VerificationApiError.SerializationError("Mock Serialization Error"))
      )
    )

  val deserializationErrorPath: ULayer[VerificationClientProvider] =
    ZLayer.succeed(
      MockVerificationClientProvider(
        Left(VerificationApiError.DeserializationError("Mock Deserialization Error"))
      )
    )
}