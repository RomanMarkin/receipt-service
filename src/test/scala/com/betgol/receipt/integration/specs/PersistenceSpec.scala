package com.betgol.receipt.integration.specs

import com.betgol.receipt.api.ReceiptRoutes
import com.betgol.receipt.api.dto.{ReceiptRequest, ReceiptSubmissionResponse}
import com.betgol.receipt.domain.models.*
import com.betgol.receipt.infrastructure.repos.mongo.mappers.MongoPrimitives.*
import com.betgol.receipt.infrastructure.repos.mongo.{MongoBonusAssignmentRepository, MongoReceiptSubmissionRepository, MongoReceiptVerificationRepository}
import com.betgol.receipt.integration.{BasicIntegrationSpec, SharedTestLayer}
import com.betgol.receipt.mocks.clients.MockVerificationClientProvider
import com.betgol.receipt.services.{ReceiptService, ReceiptServiceLive}
import org.mongodb.scala.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.{and, equal}
import zio.*
import zio.http.*
import zio.json.*
import zio.test.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*


object PersistenceSpec extends ZIOSpecDefault with BasicIntegrationSpec {

  private type TestEnv = MongoDatabase & ReceiptService

  private val successfulPath: ZLayer[Any, Throwable, TestEnv] =
    ZLayer.make[TestEnv](
      MockVerificationClientProvider.validDocPath,
      SharedTestLayer.withoutVerificationClientProvider.orDie,
      ReceiptServiceLive.layer
    )

  private val verificationDocNotFoundPath: ZLayer[Any, Throwable, TestEnv] =
    ZLayer.make[TestEnv](
      MockVerificationClientProvider.docNotFoundPath,
      SharedTestLayer.withoutVerificationClientProvider.orDie,
      ReceiptServiceLive.layer
    )

  override def spec = suite("Persistence (DB writes, DB constraints)")(

    suite("Success Scenarios")(

      test("Successful receipt submission after verification and bonus assignment saves correct document structure") {
        val validReceiptDataGen = for {
          issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
          docType = "01"
          docSeries <- Gen.elements("F001", "B001")
          docNumber <- Gen.stringN(8)(Gen.numericChar)
          total <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
          dateObj <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
          pattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
          dateStr = dateObj.format(DateTimeFormatter.ofPattern(pattern))
          receiptData = makeReceiptData(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
        } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

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
            bonusAssignmentDoc <- ZIO.fromFuture(_ =>
                bonusAssignments.find(equal("submissionId", submissionIdOpt.getOrElse("unknown"))).headOption()
              )
              .some
              .retry(retryPolicy)
              .orElseFail(s"Bonus assignment not found for submissionId: ${submissionIdOpt.getOrElse("unknown")}")

            bonusAssignmentAttempts = Option(bonusAssignmentDoc.get("attempts"))
              .filter(_.isArray)
              .map(_.asArray().getValues.asScala.map(_.asDocument()).toList)
              .getOrElse(List.empty)

            firstBonusAssignmentAttemptOpt = bonusAssignmentAttempts.headOption

          } yield assertTrue(
            // Receipt Submission assertions
            submissionDoc.getStringOpt("_id").contains(apiResponse.receiptSubmissionId),
            submissionDoc.getStringOpt("status").contains(apiResponse.status),
            submissionDoc.getStringOpt("status").contains(SubmissionStatus.BonusAssigned.toString),
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
            bonusOpt.flatMap(_.getStringOpt("status")).contains(BonusAssignmentStatus.Assigned.toString),
            bonusOpt.flatMap(_.getStringOpt("statusDescription")).contains("Mock Bonus Assigned"),
            bonusOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isBefore(before))).contains(false),
            bonusOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isAfter(after))).contains(false),
            bonusOpt.flatMap(_.getStringOpt("code")).contains("TEST_BONUS"),
            bonusOpt.flatMap(_.getStringOpt("externalId")).contains("mock-external-id"),

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
            bonusAssignmentDoc.getStringOpt("submissionId") == submissionIdOpt,
            bonusAssignmentDoc.getStringOpt("playerId").contains(playerId),
            bonusAssignmentDoc.getStringOpt("bonusCode").contains("TEST_BONUS"),
            bonusAssignmentDoc.getStringOpt("status").contains(BonusAssignmentStatus.Assigned.toString),
            bonusAssignmentAttempts.size == 1,
            firstBonusAssignmentAttemptOpt.flatMap(_.getStringOpt("status")).contains(BonusAssignmentAttemptStatus.Success.toString),
            firstBonusAssignmentAttemptOpt.flatMap(_.getIntOpt("attemptNumber")).contains(1),
            firstBonusAssignmentAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isBefore(before))).contains(false),
            firstBonusAssignmentAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isAfter(after))).contains(false),
            firstBonusAssignmentAttemptOpt.flatMap(_.getStringOpt("description")).contains("Mock Bonus Assigned")
          )
        }
      },

      test("Invalid receipt submission is saved with error") {
        val invalidReceiptDataGen = Gen.stringN(100)(Gen.alphaNumericChar)

        check(invalidReceiptDataGen) { invalidReceiptData =>
          val playerId = "player-persistence-test"
          val country = "PE"
          val req = buildRequest(ReceiptRequest(invalidReceiptData, playerId, country).toJson)

          for {
            before <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
            response <- ReceiptRoutes.routes.runZIO(req)
            after <- Clock.instant.map(_.truncatedTo(ChronoUnit.MILLIS))
            _ <- ZIO.succeed(assertTrue(response.status == Status.BadRequest))

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
            submissionDoc.getStringOpt("_id").isDefined,
            submissionDoc.getStringOpt("status").contains(SubmissionStatus.InvalidReceiptData.toString),
            submissionDoc.getStringOpt("failureReason").exists(_.nonEmpty),

            metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
            metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(invalidReceiptData),
            metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isBefore(before))).contains(false),
            metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isAfter(after))).contains(false),

            submissionDoc.getDocOpt("fiscalDocument").isEmpty,
            submissionDoc.getDocOpt("bonus").isEmpty,
            submissionDoc.getDocOpt("verification").isEmpty,
            submissionDoc.getDocOpt("bonus").isEmpty
          )
        }
      },

      test("Prevent document duplications (country + issuerTaxId + type + series + number)") {
        val uniqueNumberGen = Gen.stringN(8)(Gen.numericChar)

        check(uniqueNumberGen) { uniqueNumber =>
          val playerId = "player-id"
          val country = "PE"
          val receiptData = makeReceiptData(docNumber = uniqueNumber)
          val req = buildRequest(ReceiptRequest(receiptData, playerId, country).toJson)

          for {
            resp1 <- ReceiptRoutes.routes.runZIO(req)
            resp2 <- ReceiptRoutes.routes.runZIO(req) // Duplicate submission
            body2 <- resp2.body.asString
          } yield assertTrue(
            resp1.status == Status.Ok,
            resp2.status == Status.Conflict,
            body2.contains("Receipt already exists")
          )
        }
      }
    ).provideSomeLayer[Scope](successfulPath),

    suite ("Unverified receipt submission scenarios")(

      test("Unverified receipt is saved with verification record") {
        val validReceiptDataGen = for {
          issuerTaxId <- Gen.stringN(11)(Gen.numericChar)
          docType = "01"
          docSeries <- Gen.elements("F002", "B002")
          docNumber <- Gen.stringN(8)(Gen.numericChar)
          total <- Gen.double(10.0, 1000.0).map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
          dateObj <- Gen.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
          pattern <- Gen.elements("yyyy-MM-dd", "dd/MM/yyyy")
          dateStr = dateObj.format(DateTimeFormatter.ofPattern(pattern))
          receiptData = makeReceiptData(issuerTaxId = issuerTaxId, docType = docType, docSeries = docSeries, docNumber = docNumber, total = total, date = dateStr)
        } yield (issuerTaxId, docType, docSeries, docNumber, total, dateObj, receiptData)

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

            firstVerificationAttemptOpt = verificationAttempts.headOption

            // Bonus Assignment retrieval
            bonusAssignments = db.getCollection[BsonDocument](MongoBonusAssignmentRepository.CollectionName)
            bonusAssignmentDocOpt <- ZIO.fromFuture(_ =>
                bonusAssignments.find(equal("submissionId", submissionIdOpt.getOrElse("unknown"))).headOption()
              )
              .retry(retryPolicy)

          } yield assertTrue(
            // Receipt Submission assertions
            submissionDoc.getStringOpt("_id").contains(apiResponse.receiptSubmissionId),
            submissionDoc.getStringOpt("status").contains(apiResponse.status),
            submissionDoc.getStringOpt("status").contains(SubmissionStatus.VerificationPending.toString),

            metadataOpt.flatMap(_.getStringOpt("playerId")).contains(playerId),
            metadataOpt.flatMap(_.getStringOpt("country")).contains("PE"),
            metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isBefore(before))).contains(false),
            metadataOpt.flatMap(_.getInstantOpt("submittedAt").map(_.isAfter(after))).contains(false),
            metadataOpt.flatMap(_.getStringOpt("rawInput")).contains(receiptData),

            fiscalDocOpt.flatMap(_.getStringOpt("issuerTaxId")).contains(expectedIssuerTaxId),
            fiscalDocOpt.flatMap(_.getStringOpt("type")).contains(expectedDocType),
            fiscalDocOpt.flatMap(_.getStringOpt("series")).contains(expectedDocSeries),
            fiscalDocOpt.flatMap(_.getStringOpt("number")).contains(expectedDocNumber),
            fiscalDocOpt.flatMap(_.getLocalDateOpt("issuedAt")).contains(expectedDateObj),
            fiscalDocOpt.flatMap(_.getBigDecimalOpt("totalAmount")).map(_.toString).contains(expectedTotal),

            verificationOpt.isDefined,
            verificationOpt.flatMap(_.getStringOpt("status")).contains(ReceiptVerificationStatus.RetryScheduled.toString),
            verificationOpt.flatMap(_.getStringOpt("statusDescription")).contains("Mock Not Found"),
            verificationOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isBefore(before))).contains(false),
            verificationOpt.flatMap(_.getInstantOpt("updatedAt").map(_.isAfter(after))).contains(false),
            verificationOpt.flatMap(_.getStringOpt("apiProvider")).contains("MockProvider-Fast"),
            verificationOpt.flatMap(_.getStringOpt("externalId")).isEmpty,

            bonusOpt.isEmpty,

            // Receipt Verification assertions
            verificationDoc.getStringOpt("submissionId") == submissionIdOpt,
            verificationDoc.getStringOpt("playerId").contains(playerId),
            verificationDoc.getStringOpt("country").contains("PE"),
            verificationDoc.getStringOpt("status").contains(ReceiptVerificationStatus.RetryScheduled.toString),
            verificationAttempts.size == 1,
            firstVerificationAttemptOpt.flatMap(_.getIntOpt("attemptNumber")).contains(1),
            firstVerificationAttemptOpt.flatMap(_.getStringOpt("status")).contains(ReceiptVerificationAttemptStatus.NotFound.toString),
            firstVerificationAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isBefore(before))).contains(false),
            firstVerificationAttemptOpt.flatMap(_.getInstantOpt("attemptedAt").map(_.isAfter(after))).contains(false),
            firstVerificationAttemptOpt.flatMap(_.getStringOpt("provider")).contains("MockProvider-Fast"),
            firstVerificationAttemptOpt.flatMap(_.getStringOpt("description")).contains("Mock Not Found"),

            // Bonus Assignment assertions
            bonusAssignmentDocOpt.isEmpty
          )
        }
      }
    ).provideSomeLayer[Scope](verificationDocNotFoundPath)

  ) @@ TestAspect.withLiveClock
    @@ TestAspect.sequential
    @@ TestAspect.samples(1)
    @@ TestAspect.shrinks(1)
    @@ TestAspect.retries(0)
}
