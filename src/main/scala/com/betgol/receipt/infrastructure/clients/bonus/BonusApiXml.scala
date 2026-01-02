package com.betgol.receipt.infrastructure.clients.bonus

import com.betgol.receipt.config.BonusClientConfig
import com.betgol.receipt.domain.Ids.{BonusApiSessionCode, BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.BonusApiError.BonusRejected
import com.betgol.receipt.domain.clients.{BonusApiError, BonusAssignmentResult, BonusAssignmentResultStatus}
import zio.*

import java.time.format.DateTimeFormatter
import scala.xml.{Node, NodeSeq, XML}

object BonusApiXml {

  // --- Request builders

  def buildLoginRequest(config: BonusClientConfig, now: java.time.OffsetDateTime): String = {
    val zeroCodeSession = BonusApiSessionCode("00000000-0000-0000-0000-000000000000")
    buildEnvelope(config, "A.GE.2.2", zeroCodeSession, now, NodeSeq.Empty)
  }

  def buildAssignmentRequest(config: BonusClientConfig, sessionCode: BonusApiSessionCode, now: java.time.OffsetDateTime, playerId: PlayerId, bonusCode: BonusCode): String = {
    val body =
      <AssignedUserBonusList>
        <AssignedUserBonus>
          <UserGUID>{playerId.value}</UserGUID>
          <BonusId>{bonusCode.value}</BonusId>
        </AssignedUserBonus>
      </AssignedUserBonusList>
    buildEnvelope(config, "A.BN.1.6", sessionCode, now, body)
  }

  private def buildEnvelope(config: BonusClientConfig, actionType: String, sessionCode: BonusApiSessionCode, now: java.time.OffsetDateTime, bodyContent: NodeSeq): String = {
    val isoTime = now.format(DateTimeFormatter.ISO_INSTANT)
    val xml =
      <mtl version="2.0">
        <head>
          <appcode>{config.appCode}</appcode>
          <apptime>{isoTime}</apptime>
          <sessioncode>{sessionCode.value}</sessioncode>
          <lang>{config.lang}</lang>
        </head>
        <body>
          <action type={actionType}>
            {bodyContent}
          </action>
        </body>
      </mtl>
    xml.toString()
  }

  // --- Response parsers

  def parseLoginResponse(xmlString: String): IO[BonusApiError, BonusApiSessionCode] =
    safeLoadXml(xmlString).flatMap { root =>
      val code = (root \ "head" \ "sessioncode").text.trim
      val status = (root \ "head" \ "status").text.trim

      val isZeroSession = code == "00000000-0000-0000-0000-000000000000"
      
      if (code.nonEmpty && !isZeroSession) {
        ZIO.succeed(BonusApiSessionCode(code))
      } else {
        val msg = s"Login failed. Status: $status. Invalid SessionCode: $code"
        ZIO.fail(BonusApiError.SessionError(msg))
      }
    }

  def parseBonusResponse(xmlString: String, playerId: PlayerId): IO[BonusApiError, BonusAssignmentResult] =
    for {
      root <- safeLoadXml(xmlString)
      statusNode = (root \ "head" \ "status").text.trim
      _ <- ZIO.fail(BonusApiError.SystemError(s"API returned status $statusNode (expected 100). Body: $xmlString"))
        .when(statusNode != "100")

      actionNode = root \ "body" \ "action"
      serverCode = (actionNode \ "@servercode").text
      _ <- ZIO.fail(BonusApiError.SessionError(s"API returned servercode $serverCode: Inactive session"))
        .when(serverCode == "4381")
      
      assignedList = actionNode \ "AssignedUserBonusList" \ "AssignedUserBonus"
      isAssigned = assignedList.exists { node =>
        (node \ "UserGUID").text.trim == playerId.value
      }

      msgOpt = actionNode.headOption.flatMap(_.attribute("msg")).map(_.text)
      result <- if (isAssigned) {
        ZIO.succeed(BonusAssignmentResult(
          status = BonusAssignmentResultStatus.Assigned,
          description = msgOpt,
          externalId = None
        ))
      } else {
        ZIO.fail(BonusRejected(msgOpt.getOrElse(s"Bonus not assigned for player $playerId")))
      }
    } yield result

  // --- Helpers

  private def safeLoadXml(xmlString: String): IO[BonusApiError, Node] = {
    ZIO.attempt(XML.loadString(xmlString))
      .mapError(e => BonusApiError.SystemError(s"Failed to parse XML response: ${e.getMessage}", e))
      .tapError(err => ZIO.logError(s"XML Parsing Failed. Error: ${err.msg} | Payload: $xmlString"))
  }
}