package com.betgol.receipt.integration.specs.validation

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.infrastructure.repos.mongo.MongoReceiptSubmissionRepository
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.equal
import zio.http.*
import zio.json.*
import zio.test.*
import zio.{Clock, Schedule, Scope, ZIO, durationInt}

import java.time.temporal.ChronoUnit


object UnparsableReceiptSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.validDocPath,
    MockBonusService.bonusAssignedPath
  )

  val suiteSpec = suite("Persistence: Unparsable Receipt Handling")(

    test("Persists unparsable data (garbage string) as 'InvalidReceiptData' without creating fiscal document") {
      val invalidReceiptDataGen = Gen.stringN(100)(Gen.alphaNumericChar)

      check(invalidReceiptDataGen) { invalidReceiptData =>
        val playerId = "player-persistence-test"
        val country = "PE"
        val req = buildRequest(ReceiptRequest(invalidReceiptData, playerId, country).toJson)

        for {
          before <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          response <- ReceiptRoutes.routes.runZIO(req)
          after <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          body <- response.body.asString
          apiResponse <- ZIO.fromEither(body.fromJson[ReceiptSubmissionResponse]).orElseFail(s"Failed to parse API response: $body")
          db <- ZIO.service[MongoDatabase]
          submissions = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          submissionDoc <- ZIO.fromFuture(_ =>
              submissions.find(equal("metadata.rawInput", invalidReceiptData)).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"ReceiptSubmission not found for rawInput: $invalidReceiptData")

          metadataOpt = submissionDoc.getDocOpt("metadata")

        } yield assertTrue(
          // API Response assertions
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.InvalidReceiptData.toString,
          apiResponse.message.contains("Insufficient data fields"),

          submissionDoc.getStringOpt("_id").isDefined,
          submissionDoc.getStringOpt("status").contains(SubmissionStatus.InvalidReceiptData.toString),
          submissionDoc.getStringOpt("statusDescription").contains("Insufficient data fields"),

          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(invalidReceiptData),
          metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isBefore(before))).contains(false),
          metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isAfter(after))).contains(false),

          submissionDoc.getDocOpt("fiscalDocument").isEmpty,
          submissionDoc.getDocOpt("verification").isEmpty,
          submissionDoc.getDocOpt("bonus").isEmpty
        )
      }
    }

  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)
}