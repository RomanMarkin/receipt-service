package com.betgol.receipt.domain.clients

import zio._
import com.betgol.receipt.domain.{ParsedReceipt, TaxAuthorityConfirmation}


trait FiscalApiClient {
  def providerName: String

  /**
   * Verifies the receipt with the external authority.
   * Returns:
   * - Some(confirmation): If found and valid.
   * - None: If the provider responded "Not Found".
   * - Fail(Throwable): If network error or 5xx response.
   */
  def verify(receipt: ParsedReceipt): IO[Throwable, Option[TaxAuthorityConfirmation]]
}