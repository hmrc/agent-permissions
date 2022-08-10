/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentpermissions.service.userenrolment

import org.scalamock.handlers.CallHandler4
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Enrolment, Identifier}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class GroupClientsRemoverSpec extends BaseSpec {

  "Removing clients from access group" when {

    "removal enrolments contain some that exist in access group" should {
      "remove only those matching enrolments of access group" in new TestScope {
        val accessGroup: AccessGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))

        val removalEnrolments: Set[Enrolment] = Set(enrolmentPpt, enrolmentCgt, enrolmentTrust)

        mockAuditConnectorSendExplicitAudit(
          "AccessGroupClientsRemoval",
          buildAuditDetailForClientsRemoval(accessGroup, Set(enrolmentPpt, enrolmentCgt))
        )

        val accessGroupWithClientsRemoved: AccessGroup =
          groupClientsRemover.removeClientsFromGroup(accessGroup, removalEnrolments, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe Some(Set(enrolmentVat))
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
      }
    }

    "removal enrolments do not contain any that exist in access group" should {
      "not remove any enrolments of access group" in new TestScope {
        val accessGroup: AccessGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))

        val removalEnrolments: Set[Enrolment] = Set(enrolmentTrust)

        val accessGroupWithClientsRemoved: AccessGroup =
          groupClientsRemover.removeClientsFromGroup(accessGroup, removalEnrolments, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe accessGroup.clients
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
      }
    }
  }

  trait TestScope {

    val mockAuditConnector: AuditConnector = mock[AuditConnector]

    val groupClientsRemover = new GroupClientsRemoverImpl(mockAuditConnector)

    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName")
    val now: LocalDateTime = LocalDateTime.now()

    val enrolmentVat: Enrolment =
      Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))

    val enrolmentPpt: Enrolment = Enrolment(
      "HMRC-PPT-ORG",
      "Activated",
      "Frank Wright",
      Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
    )

    val enrolmentCgt: Enrolment =
      Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CGTPDRef", "XMCGTP123456789")))

    val enrolmentTrust: Enrolment =
      Enrolment("HMRC-TERS-ORG", "Activated", "Trust Client", Seq(Identifier("SAUTR", "0123456789")))

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Enrolment]]): AccessGroup =
      AccessGroup(arn, groupName, now, now, agentUser1, agentUser1, teamMembers, clients)

    def buildAuditDetailForClientsRemoval(accessGroup: AccessGroup, enrolments: Set[Enrolment]): JsObject =
      Json.obj(
        "accessGroupId"   -> s"${accessGroup._id}",
        "accessGroupName" -> s"${accessGroup.groupName}",
        "clientsRemoved"  -> enrolments
      )

    def mockAuditConnectorSendExplicitAudit(
      auditType: String,
      detail: JsObject
    ): CallHandler4[String, JsObject, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditConnector
        .sendExplicitAudit(_: String, _: JsObject)(_: HeaderCarrier, _: ExecutionContext))
        .expects(auditType, detail, *, *)
        .returning(())
  }

}
