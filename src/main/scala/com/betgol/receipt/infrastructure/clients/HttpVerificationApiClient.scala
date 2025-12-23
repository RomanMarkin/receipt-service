package com.betgol.receipt.infrastructure.clients

import com.betgol.receipt.domain.clients.{VerificationApiClient, VerificationApiError}
import zio.{IO, ZIO}


abstract class HttpVerificationApiClient extends VerificationApiClient {
  /**
   * Default HTTP validation.
   * Overridable if a specific provider uses 404 for "Business Not Found" instead of "Endpoint Not Found".
   */
  protected def validateHttpStatus(code: Int, body: String): IO[VerificationApiError, Unit] =
    code match {
      case c if c >= 500 =>
        ZIO.fail(VerificationApiError.ServerError(c, s"[$providerName] Upstream Server Error"))
      case 429 =>
        ZIO.fail(VerificationApiError.ServerError(429, s"[$providerName] Rate Limit Exceeded"))
      case 401 | 403 =>
        ZIO.fail(VerificationApiError.ClientError(code, s"[$providerName] Auth Failed (Check API Key)"))
      case c if c >= 400 =>
        ZIO.fail(VerificationApiError.ClientError(c, s"[$providerName] Client Error. Body: $body"))
      case _ =>
        ZIO.unit
    }
}