package com.betgol.receipt.infrastructure.clients.bonus

import com.betgol.receipt.config.BonusClientConfig
import com.betgol.receipt.domain.Ids.{BonusApiSessionCode, BonusCode, PlayerId}
import com.betgol.receipt.domain.clients.{BonusApiError, BonusAssignmentResultStatus}
import zio.test.*

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.xml.{Utility, XML}


object BonusApiXmlSpec extends ZIOSpecDefault {

  private val config = BonusClientConfig(url = "http://localhost", appCode = "TEST_APP_CODE", lang = "en", timeoutSeconds = 5)
  private val now = OffsetDateTime.now()
  private val session = BonusApiSessionCode("TEST-SESSION")
  private val player = PlayerId("player-123")
  private val bonus = BonusCode("BONUS-100")

  def spec = suite("BonusApiXml Logic")(

    suite("Request Building")(

      test("Login request matches expected XML structure") {
        val actualString = BonusApiXml.buildLoginRequest(config, now)
        val actualXml = XML.loadString(actualString)

        val expectedXml =
          <mtl version="2.0">
            <head>
              <appcode>{config.appCode}</appcode>
              <apptime>{now.format(DateTimeFormatter.ISO_INSTANT)}</apptime>
              <sessioncode>00000000-0000-0000-0000-000000000000</sessioncode>
              <lang>{config.lang}</lang>
            </head>
            <body>
              <action type="A.GE.2.2"/>
            </body>
          </mtl>

        assertTrue(Utility.trim(actualXml).toString() == Utility.trim(expectedXml).toString())
      },

      test("Assignment request matches expected XML structure") {
        val actualString = BonusApiXml.buildAssignmentRequest(config, session, now, player, bonus)
        val actualXml = XML.loadString(actualString)

        val expectedXml =
          <mtl version="2.0">
            <head>
              <appcode>{config.appCode}</appcode>
              <apptime>{now.format(DateTimeFormatter.ISO_INSTANT)}</apptime>
              <sessioncode>{session.value}</sessioncode>
              <lang>{config.lang}</lang>
            </head>
            <body>
              <action type="A.BN.1.6">
                <AssignedUserBonusList>
                  <AssignedUserBonus>
                    <UserId>{player.value}</UserId>
                    <BonusId>{bonus.value}</BonusId>
                  </AssignedUserBonus>
                </AssignedUserBonusList>
              </action>
            </body>
          </mtl>

        assertTrue(Utility.trim(actualXml).toString() == Utility.trim(expectedXml).toString())
      }
    ),

    suite("Response Parsing")(
      test("Parses successful Login response") {
        val xmlNode =
          <mtl>
            <head>
              <sessioncode>NEW-SESSION-CODE</sessioncode>
            </head>
          </mtl>
        for {
          code <- BonusApiXml.parseLoginResponse(xmlNode.toString)
        } yield assertTrue(code.value == "NEW-SESSION-CODE")
      },

      test("Parses successful Bonus Assignment") {
        val xmlNode =
          <mtl>
            <head>
              <status>100</status>
            </head>
            <body>
              <action msg="Success">
                <AssignedUserBonusList>
                  <AssignedUserBonus>
                    <UserId>{player.value}</UserId>
                    <BonusId>71</BonusId>
                  </AssignedUserBonus>
                </AssignedUserBonusList>
              </action>
            </body>
          </mtl>
        for {
          result <- BonusApiXml.parseBonusResponse(xmlNode.toString(), player)
        } yield assertTrue(
          result.status == BonusAssignmentResultStatus.Assigned,
          result.description.contains("Success")
        )
      },

      test("Detects Session Expired error (servercode 4381)") {
        val xmlNode =
          <mtl>
            <head>
              <status>100</status>
            </head>
            <body>
              <action servercode="4381" msg="Session expired"/>
            </body>
          </mtl>
        for {
          exit <- BonusApiXml.parseBonusResponse(xmlNode.toString, player).exit
        } yield assertTrue(
          exit.isFailure,
          exit.causeOption.get.failures.head.asInstanceOf[BonusApiError.SessionError].msg.contains("4381")
        )
      },

      test("Detects Bonus Rejection (Player ID not in assigned list)") {
        val xmlNode =
          <mtl>
            <head>
              <status>100</status>
            </head>
            <body>
              <action msg="User not eligible">
                <AssignedUserBonusList>
                </AssignedUserBonusList>
              </action>
            </body>
          </mtl>
        for {
          exit <- BonusApiXml.parseBonusResponse(xmlNode.toString, player).exit
        } yield assertTrue(
          exit.isFailure,
          exit.causeOption.get.failures.head.isInstanceOf[BonusApiError.BonusRejected]
        )
      },

      test("Detects Bonus Rejection (Player ID is in unassigned list)") {
        val xmlNode =
          <mtl>
            <head>
              <status>100</status>
            </head>
            <body>
              <action msg="User not eligible">
                <UnAssignedUserBonusList>
                  <UnAssignedUserBonus>
                    <UserId>{player.value}</UserId>
                    <BonusId>71</BonusId>
                  </UnAssignedUserBonus>
                </UnAssignedUserBonusList>
              </action>
            </body>
          </mtl>
        for {
          exit <- BonusApiXml.parseBonusResponse(xmlNode.toString, player).exit
        } yield assertTrue(
          exit.isFailure,
          exit.causeOption.get.failures.head.isInstanceOf[BonusApiError.BonusRejected]
        )
      }
    )
  )
}