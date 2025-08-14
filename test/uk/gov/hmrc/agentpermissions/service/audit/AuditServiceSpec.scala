/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.service.audit

import org.scalamock.handlers.{CallHandler, CallHandler0}
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.{UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, CustomGroup, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class AuditServiceSpec extends BaseSpec with AuditTestSupport {

  "Access group creation" should {
    "audit event correctly" when {
      "custom group" in new TestScope {
        val accessGroup: CustomGroup =
          buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt))

        mockAppConfigAccessGroupChunkSize(1000)
        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupCreated", 2)

        auditService.auditAccessGroupCreation(accessGroup)
      }

      "tax group" in new TestScope {
        val taxGroup: TaxGroup = buildTaxGroup(Set(agentUser1, agentUser2), Set(clientVat), serviceVat)
        mockAppConfigAccessGroupChunkSize(1000)
        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupCreated", 2)

        auditService.auditAccessGroupCreation(taxGroup)
      }
    }
  }

  "Access group update" should {
    "audit event correctly" when {
      "custom group" in new TestScope {
        val accessGroup: CustomGroup =
          buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt))

        mockAppConfigAccessGroupChunkSize(1000)
        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupUpdated", 2)

        auditService.auditAccessGroupUpdate(accessGroup)
      }

      "tax group" in new TestScope {
        val taxGroup: TaxGroup = buildTaxGroup(Set(agentUser1, agentUser2), Set(clientVat), serviceVat)
        mockAppConfigAccessGroupChunkSize(1000)
        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupUpdated", 2)

        auditService.auditAccessGroupUpdate(taxGroup)
      }
    }

  }

  "Access group deletion" should {
    "audit event correctly" when {
      "custom group" in new TestScope {
        val accessGroup: CustomGroup =
          buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt))

        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupDeleted", 1)

        auditService.auditAccessGroupDeletion(accessGroup.arn, accessGroup.groupName, agentUser1)
      }

      "tax group" in new TestScope {
        val taxGroup: TaxGroup = buildTaxGroup(Set(agentUser1, agentUser2), Set(clientVat), serviceVat)
        mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupDeleted", 1)

        auditService.auditAccessGroupDeletion(taxGroup.arn, taxGroup.groupName, agentUser1)
      }
    }
  }

  "ES Assignments/Unassignments" should {
    "audit event correctly" in new TestScope {
      mockAppConfigUserEnrolmentAssignmentsChunkSize(1000)
      mockAuditConnectorSendExplicitAudit("GranularPermissionsESAssignmentsUnassignmentsPushed", 2)

      auditService.auditEsAssignmentUnassignments(userEnrolmentAssignments)
    }
  }

  "Granular Permissions optin" should {
    "audit event correctly" in new TestScope {
      mockAuditConnectorSendExplicitAudit("GranularPermissionsOptedIn", 1)

      auditService.auditOptInEvent(arn, agentUser1)
    }
  }

  "Granular Permissions optout" should {
    "audit event correctly" in new TestScope {
      mockAuditConnectorSendExplicitAudit("GranularPermissionsOptedOut", 1)

      auditService.auditOptOutEvent(arn, agentUser1)
    }
  }

  "Access group clients removal" should {
    "audit event correctly" in new TestScope {
      val accessGroup: CustomGroup =
        buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt))

      val chunkSize = 1000

      mockAppConfigClientsRemovalChunkSize(chunkSize)
      mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupClientsRemoval", 2)

      auditService.auditAccessGroupClientsRemoval(accessGroup, (1 to chunkSize + 1).map(_ => client()).toSet)
    }
  }

  "Access group team members removal" should {
    "audit event correctly" in new TestScope {
      val accessGroup: CustomGroup =
        buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt))

      val chunkSize = 1000

      mockAppConfigTeamMembersRemovalChunkSize(chunkSize)
      mockAuditConnectorSendExplicitAudit("GranularPermissionsAccessGroupTeamMembersRemoval", 2)

      auditService.auditAccessGroupTeamMembersRemoval(
        accessGroup,
        (1 to chunkSize + 1).map(_ => teamMember()).toSet
      )
    }
  }

  trait TestScope {
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName")
    val agentUser2: AgentUser = AgentUser("userId2", "userName")
    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")
    val userEnrolmentAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(
      Set(
        UserEnrolment(agentUser1.id, clientCgt.enrolmentKey),
        UserEnrolment(agentUser1.id, clientCgt.enrolmentKey)
      ),
      Set(
        UserEnrolment(agentUser1.id, clientPpt.enrolmentKey)
      ),
      arn
    )
    val now: LocalDateTime = LocalDateTime.now()

    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockAppConfig: AppConfig = mock[AppConfig]

    val auditService = new AuditServiceImpl(mockAuditConnector)

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def buildCustomGroup(teamMembers: Set[AgentUser], clients: Set[Client]): CustomGroup =
      CustomGroup(
        GroupId.random(),
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

    def buildTaxGroup(teamMembers: Set[AgentUser], clients: Set[Client], service: String): TaxGroup =
      TaxGroup(
        GroupId.random(),
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        service,
        automaticUpdates = true,
        clients
      )

    def mockAppConfigAccessGroupChunkSize(chunkSize: Int): CallHandler0[Int] =
      (() => mockAppConfig.accessGroupChunkSize)
        .expects()
        .returning(chunkSize)

    def mockAppConfigUserEnrolmentAssignmentsChunkSize(chunkSize: Int): CallHandler0[Int] =
      (() => mockAppConfig.useEnrolmentAssignmentsChunkSize)
        .expects()
        .returning(chunkSize)

    def mockAppConfigClientsRemovalChunkSize(chunkSize: Int): CallHandler0[Int] =
      (() => mockAppConfig.clientsRemovalChunkSize)
        .expects()
        .returning(chunkSize)

    def mockAppConfigTeamMembersRemovalChunkSize(chunkSize: Int): CallHandler0[Int] =
      (() => mockAppConfig.teamMembersRemovalChunkSize)
        .expects()
        .returning(chunkSize)

    def mockAuditConnectorSendExplicitAudit(
      eventType: String,
      times: Int
    ): CallHandler[Unit] =
      (mockAuditConnector
        .sendExplicitAudit(_: String, _: JsObject)(_: HeaderCarrier, _: ExecutionContext))
        .expects(eventType, *, *, *)
        .returning(())
        .repeat(times)

  }
}
