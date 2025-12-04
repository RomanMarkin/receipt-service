package com.betgol.receipt.service

import com.betgol.receipt.domain.*
import com.betgol.receipt.domain.Types.*
import com.betgol.receipt.domain.clients.*
import com.betgol.receipt.domain.parsing.ReceiptParser
import com.betgol.receipt.domain.repo.*
import zio.test.*
import zio.test.Assertion.*
import zio.*

import java.time.{Instant, LocalDate}


object ReceiptServiceRaceSpec extends ZIOSpecDefault {


  case class DelayedMockClient(name: String,
                               delay: Duration,
                               result: Option[TaxAuthorityConfirmation]) extends FiscalApiClient {
    override def providerName: String = name

    override def verify(receipt: ParsedReceipt): IO[Throwable, Option[TaxAuthorityConfirmation]] = {
      ZIO.sleep(delay) *> ZIO.succeed(result) //ZIO.sleep() is using ZIO Clock, which is controlled by TestClock
    }
  }

  case class MockClientProvider(clients: List[FiscalApiClient]) extends FiscalClientProvider {
    def getClientsFor(countryIso: CountryIsoCode): List[FiscalApiClient] = clients
  }

  val dummyReceipt = ParsedReceipt("123", "01", "F001", "1", 10.0, LocalDate.now(), CountryIsoCode("PE"))

  // Mock dependencies
  val mockParser = new ReceiptParser {
    def parse(raw: String) = ZIO.succeed(dummyReceipt)
  }
  val mockReceiptRepo = new ReceiptRepository {
    def saveValid(p: PlayerId, r: String, pr: ParsedReceipt) = ZIO.succeed(ReceiptId("id"))
    def saveInvalid(p: PlayerId, r: String, e: String) = ZIO.unit
    def updateConfirmed(id: ReceiptId, c: TaxAuthorityConfirmation) = ZIO.unit
  }
  val mockReceiptRetryRepo = new ReceiptRetryRepository {
    def save(id: ReceiptId, p: PlayerId, c: CountryIsoCode) = ZIO.unit
  }

  def buildService(clients: List[FiscalApiClient]) = {
    ZLayer.succeed(mockParser) ++
    ZLayer.succeed(mockReceiptRepo) ++
    ZLayer.succeed(mockReceiptRetryRepo) ++
    ZLayer.succeed(MockClientProvider(clients)) >>>
    ZLayer.fromFunction(ReceiptServiceLive.apply _)
  }

  override def spec = suite("ReceiptService Racing Logic")(

    test("Scenario 1: Fastest client wins") {
      val fastConfirmation = TaxAuthorityConfirmation("Fast", Instant.now(), "1", "OK")
      val slowConfirmation = fastConfirmation.copy(apiProvider = "Slow")

      val clientFast = DelayedMockClient("Fast", 1.second, Some(fastConfirmation))
      val clientSlow = DelayedMockClient("Slow", 5.seconds, Some(slowConfirmation))

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(2.second) // advance time between the completion of Fast and Slow
        result <- fiber.join
      } yield assert(result)(isSome(equalTo(fastConfirmation))))
        .provide(buildService(List(clientFast, clientSlow)))
    },

    test("Scenario 2: If first client fails, second client wins") {
      val slowConfirmation = TaxAuthorityConfirmation("SlowButSuccess", Instant.now(), "2", "OK")

      val clientFastFail = DelayedMockClient("FastFail", 100.millis, None)
      val clientSlowSuccess = DelayedMockClient("SlowSuccess", 2.seconds, Some(slowConfirmation))

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(2.second) // advance time after the completion of SlowSuccess
        result <- fiber.join
      } yield assert(result)(isSome(equalTo(slowConfirmation))))
        .provide(buildService(List(clientFastFail, clientSlowSuccess)))
    },

    test("Scenario 3: All clients are too slow (total timeout)") {
      val clientTooSlow1 = DelayedMockClient("Slow1", 10.seconds, None)
      val clientTooSlow2 = DelayedMockClient("Slow2", 10.seconds, None)

      (for {
        fiber <- ZIO.serviceWithZIO[ReceiptServiceLive](_.verifyWithTaxAuthority(dummyReceipt)).fork
        _ <- TestClock.adjust(5.second)
        result <- fiber.join
      } yield assert(result)(isNone))
        .provide(buildService(List(clientTooSlow1, clientTooSlow2)))
    }
  )
}