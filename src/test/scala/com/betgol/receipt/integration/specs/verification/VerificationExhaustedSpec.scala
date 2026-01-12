package com.betgol.receipt.integration.specs.verification

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.infrastructure.repos.mongo.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
import com.betgol.receipt.jobs.ReceiptVerificationRetryJob
import com.betgol.receipt.mocks.services.{MockBonusService, MockVerificationService}
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal}
import zio.http.*
import zio.json.*
import zio.test.*
import zio.{Clock, Schedule, Scope, ZIO, durationInt}

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*


object VerificationExhaustedSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.networkErrorPath,
    MockBonusService.bonusAssignedPath,
  ) >+> MongoReceiptVerificationJobStatsRepository.layer

  val suiteSpec = suite("Receipt Verification: Verification attempts exhausted")(

    test("Verification eventually failed because of exhausting after maximum unsuccessful attempts being made") {
      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val country = "PE"
        val req = buildRequest(ReceiptRequest(receiptData, playerId, country).toJson)

        for {
          before <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          response <- ReceiptRoutes.routes.runZIO(req)

          _ <- Clock.sleep(100.millis)
          statsOnRetry2 <- ReceiptVerificationRetryJob.run

          _ <- Clock.sleep(100.millis)
          statsOnRetry3 <- ReceiptVerificationRetryJob.run

          after <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          body <- response.body.asString
          apiResponse <- ZIO.fromEither(body.fromJson[ReceiptSubmissionResponse]).orElseFail(s"Failed to parse API response: $body")
          db <- ZIO.service[MongoDatabase]
          submissions = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

          // Receipt Submission retrieval
          submissionDoc <- ZIO.fromFuture(_ =>
              submissions.find(
                and(
                  equal("fiscalDocument.issuerTaxId", expectedIssuerTaxId),
                  equal("fiscalDocument.type", expectedDocType),
                  equal("fiscalDocument.series", expectedDocSeries),
                  equal("fiscalDocument.number", expectedDocNumber)
                )
              ).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail("ReceiptSubmission not found")

          submissionIdOpt = submissionDoc.getStringOpt("_id")
          fiscalDocOpt = submissionDoc.getDocOpt("fiscalDocument")
          metadataOpt = submissionDoc.getDocOpt("metadata")
          verificationOpt = submissionDoc.getDocOpt("verification")
          bonusOpt = submissionDoc.getDocOpt("bonus")

          // Receipt Verification retrieval
          verifications = db.getCollection[BsonDocument](MongoReceiptVerificationRepository.CollectionName)
          verificationDoc <- ZIO.fromFuture(_ =>
              verifications.find(equal("submissionId", submissionIdOpt.getOrElse("unknown"))).headOption()
            )
            .some
            .retry(retryPolicy)
            .orElseFail(s"Receipt verification not found for submissionId: ${submissionIdOpt.getOrElse("unknown")}")

          verificationAttempts = Option(verificationDoc.get("attempts"))
            .filter(_.isArray)
            .map(_.asArray().getValues.asScala.map(_.asDocument()).toList)
            .getOrElse(List.empty)
          
          // Bonus Assignment retrieval
          bonusAssignments = db.getCollection[BsonDocument](MongoBonusAssignmentRepository.CollectionName)
          bonusAssignmentDocOpt <- ZIO.fromFuture(_ =>
              bonusAssignments.find(equal("submissionId", submissionIdOpt.getOrElse("unknown"))).headOption()
            )
            .retry(retryPolicy)

        } yield assertTrue(
          // API Response assertions
          response.status == Status.Ok,
          apiResponse.receiptSubmissionId.isValidUuid,
          apiResponse.status == SubmissionStatus.VerificationPending.toString,
          apiResponse.message.isEmpty,

          // Receipt Submission assertions
          submissionDoc.getStringOpt("_id").contains(apiResponse.receiptSubmissionId),
          submissionDoc.getStringOpt("status").contains(SubmissionStatus.VerificationFailed.toString),
          submissionDoc.getStringOpt("statusDescription").contains("All providers failed. Last error: Mock Network Error"),

          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          metadataOpt.flatMap(_.getStringOpt("country")).contains("PE"),
          metadataOpt.flatMap(_.getInstantOpt("submittedAt")).exists(t => !t.isBefore(before) && !t.isAfter(after)),
          metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(receiptData),

          fiscalDocOpt.flatMap(_.getStringOpt("issuerTaxId")).contains(expectedIssuerTaxId),
          fiscalDocOpt.flatMap(_.getStringOpt("type")).contains(expectedDocType),
          fiscalDocOpt.flatMap(_.getStringOpt("series")).contains(expectedDocSeries),
          fiscalDocOpt.flatMap(_.getStringOpt("number")).contains(expectedDocNumber),
          fiscalDocOpt.flatMap(_.getLocalDateOpt("issuedAt")).contains(expectedDateObj),
          fiscalDocOpt.flatMap(_.getBigDecimalOpt("totalAmount")).map(_.toString).contains(expectedTotal),

          verificationOpt.isDefined,
          verificationOpt.flatMap(_.getStringOpt("status")).contains(ReceiptVerificationStatus.Exhausted.toString),
          verificationOpt.flatMap(_.getStringOpt("statusDescription")).contains("All providers failed. Last error: Mock Network Error"),
          verificationOpt.flatMap(_.getInstantOpt("updatedAt")).exists(t => !t.isBefore(before) && !t.isAfter(after)),
          verificationOpt.flatMap(_.getStringOpt("apiProvider")).isEmpty,
          verificationOpt.flatMap(_.getStringOpt("externalId")).isEmpty,

          bonusOpt.isEmpty,

          // Receipt Verification Job run #1 assertions
          statsOnRetry2.processed == 1,
          statsOnRetry2.succeeded == 0,
          statsOnRetry2.failed == 0,
          statsOnRetry2.rejected == 0,
          statsOnRetry2.rescheduled == 1,

          // Receipt Verification Job run #2 assertions
          statsOnRetry3.processed == 1,
          statsOnRetry3.succeeded == 0,
          statsOnRetry3.failed == 1,
          statsOnRetry3.rejected == 0,
          statsOnRetry3.rescheduled == 0,

          // Receipt Verification assertions
          verificationDoc.getStringOpt("submissionId") == submissionIdOpt,
          verificationDoc.getStringOpt("playerId").contains(playerId),
          verificationDoc.getStringOpt("country").contains("PE"),
          verificationDoc.getStringOpt("status").contains(ReceiptVerificationStatus.Exhausted.toString),
          verificationDoc.getInstantOpt("nextRetryAt").isEmpty,
          verificationDoc.getInstantOpt("updatedAt").exists(t => !t.isBefore(before) && !t.isAfter(after)),
          verificationDoc.getInstantOpt("createdAt").exists(t => !t.isBefore(before) && !t.isAfter(after)),
          verificationAttempts.size == 3,

          (0 to 2).forall { i =>
            verificationAttempts.lift(i).exists { attempt =>
              attempt.getIntOpt("attemptNumber").contains(i + 1) &&
                attempt.getStringOpt("status").contains(ReceiptVerificationAttemptStatus.SystemError.toString) &&
                attempt.getInstantOpt("attemptedAt").exists(t => !t.isBefore(before) && !t.isAfter(after)) &&
                attempt.getStringOpt("provider").isEmpty &&
                attempt.getStringOpt("description").contains("All providers failed. Last error: Mock Network Error")
            }
          },

          // Bonus Assignment assertions
          bonusAssignmentDocOpt.isEmpty
        )
      }
    }
  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)
}