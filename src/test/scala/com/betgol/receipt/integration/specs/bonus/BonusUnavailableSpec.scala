package com.betgol.receipt.integration.specs.bonus

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.integration.{SharedTestLayer, TestHelpers, TestSuiteLayer}
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


object BonusUnavailableSpec extends TestHelpers {

  private val layer = TestSuiteLayer.make(
    MockVerificationService.validDocPath,
    MockBonusService.bonusNotAvailablePath
  )

  val suiteSpec = suite ("Bonus Assignment: Bonus Unavailable")(
    test("Completes workflow with 'NoBonusAvailable' status when no bonus matches (skips assignment document)"){
      check(validReceiptDataGen) { case (expectedIssuerTaxId, expectedDocType, expectedDocSeries, expectedDocNumber, expectedTotal, expectedDateObj, receiptData) =>
        val playerId = "player-persistence-test"
        val country = "PE"
        val req = buildRequest(ReceiptRequest(receiptData, playerId, country).toJson)

        for {
          before <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          response <- ReceiptRoutes.routes.runZIO(req)
          after <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
          body <- response.body.asString
          apiResponse <- ZIO.fromEither(body.fromJson[ReceiptSubmissionResponse]).orElseFail(s"Failed to parse API response: $body")
          _ <- ZIO.succeed(assertTrue(response.status == Status.Ok))
          db <- ZIO.service[MongoDatabase]

          // Receipt Submission assertions
          submissions = db.getCollection[BsonDocument](MongoReceiptSubmissionRepository.CollectionName)
          retryPolicy = Schedule.recurs(10) && Schedule.spaced(100.millis)

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
            .orElseFail(s"ReceiptSubmission not found for issuer: $expectedIssuerTaxId, number: $expectedDocNumber")

          submissionIdOpt = submissionDoc.getStringOpt("_id")
          metadataOpt = submissionDoc.getDocOpt("metadata")
          fiscalDocOpt = submissionDoc.getDocOpt("fiscalDocument")
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

          firstVerificationAttemptOpt = verificationAttempts.headOption

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
          apiResponse.status == SubmissionStatus.NoBonusAvailable.toString,
          apiResponse.message.isEmpty,

          // Receipt Submission assertions
          submissionDoc.getStringOpt("_id").contains(apiResponse.receiptSubmissionId),
          submissionDoc.getStringOpt("status").contains(apiResponse.status),
          submissionDoc.getStringOpt("status").contains(SubmissionStatus.NoBonusAvailable.toString),
          submissionDoc.getStringOpt("failureReason").isEmpty,

          metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
          metadataOpt.flatMap(_.getStringOpt("country")).contains("PE"),
          metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(receiptData),
          metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isBefore(before))).contains(false),
          metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isAfter(after))).contains(false),

          fiscalDocOpt.isDefined,
          fiscalDocOpt.flatMap(_.getStringOpt("issuerTaxId")).contains(expectedIssuerTaxId),
          fiscalDocOpt.flatMap(_.getStringOpt("type")).contains(expectedDocType),
          fiscalDocOpt.flatMap(_.getStringOpt("series")).contains(expectedDocSeries),
          fiscalDocOpt.flatMap(_.getStringOpt("number")).contains(expectedDocNumber),
          fiscalDocOpt.flatMap(_.getLocalDateOpt("issuedAt")).contains(expectedDateObj),
          fiscalDocOpt.flatMap(_.getBigDecimalOpt("totalAmount")).map(_.toString).contains(expectedTotal),

          verificationOpt.isDefined,
          verificationOpt.flatMap(_.getStringOpt("status")).contains(ReceiptVerificationStatus.Confirmed.toString),
          verificationOpt.flatMap(_.getStringOpt("statusDescription")).contains("Mock Valid"),
          verificationOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isBefore(before))).contains(false),
          verificationOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isAfter(after))).contains(false),
          verificationOpt.flatMap(_.getStringOpt("apiProvider")).contains("MockProvider-Fast"),
          verificationOpt.flatMap(_.getStringOpt("externalId")).contains("mocked-external-id"),

          bonusOpt.isDefined,
          bonusOpt.flatMap(_.getStringOpt("status")).contains(BonusAssignmentStatus.NoBonus.toString),
          bonusOpt.flatMap(_.getStringOpt("statusDescription")).isEmpty,
          bonusOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isBefore(before))).contains(false),
          bonusOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isAfter(after))).contains(false),
          bonusOpt.flatMap(_.getStringOpt("code")).isEmpty,
          bonusOpt.flatMap(_.getStringOpt("externalId")).isEmpty,

          // Receipt Verification assertions
          verificationDoc.getStringOpt("submissionId") == submissionIdOpt,
          verificationDoc.getStringOpt("playerId").contains(playerId),
          verificationDoc.getStringOpt("country").contains("PE"),
          verificationDoc.getStringOpt("status").contains(ReceiptVerificationStatus.Confirmed.toString),
          verificationAttempts.size == 1,
          firstVerificationAttemptOpt.flatMap(_.getStringOpt("status")).contains(ReceiptVerificationAttemptStatus.Valid.toString),
          firstVerificationAttemptOpt.flatMap(_.getIntOpt("attemptNumber")).contains(1),
          firstVerificationAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isBefore(before))).contains(false),
          firstVerificationAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isAfter(after))).contains(false),
          firstVerificationAttemptOpt.flatMap(_.getStringOpt("provider")).contains("MockProvider-Fast"),
          firstVerificationAttemptOpt.flatMap(_.getStringOpt("description")).contains("Mock Valid"),

          // Bonus Assignment assertions
          bonusAssignmentDocOpt.isEmpty
        )
      }
    }

  ).provideSomeLayer[SharedTestLayer.InfraEnv & Scope](layer)
}