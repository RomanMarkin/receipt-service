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
   * - Fail(FiscalApiError): If serialization/deserialization or network/5xx errors.
   */
  def verify(receipt: ParsedReceipt): IO[FiscalApiError, Option[TaxAuthorityConfirmation]]
}