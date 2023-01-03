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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import org.scalamock.handlers.CallHandler4
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Client}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class GroupTeamMembersRemoverSpec extends BaseSpec {

  "Removing team members from access group" when {

    "removal user ids contain some that exist in access group" should {
      "remove only those matching user ids of access group" in new TestScope {
        val accessGroup: AccessGroup =
          buildAccessGroup(Some(Set(agentUser1, agentUser2)), Some(Set(clientVat)))

        val removalUserIds: Set[String] = Set(agentUser2.id)

        mockAuditServiceAuditAccessGroupTeamMembersRemoval()

        val accessGroupWithClientsRemoved: AccessGroup =
          groupTeamMembersRemover.removeTeamMembersFromGroup(accessGroup, removalUserIds, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe accessGroup.clients
        accessGroupWithClientsRemoved.teamMembers shouldBe Some(Set(agentUser1))
      }
    }

    "removal user ids do not contain any that exist in access group" should {
      "not remove any user ids of access group" in new TestScope {
        val accessGroup: AccessGroup =
          buildAccessGroup(Some(Set(agentUser1, agentUser2)), Some(Set(clientVat)))

        val removalUserIds: Set[String] = Set("unknown")

        val accessGroupWithClientsRemoved: AccessGroup =
          groupTeamMembersRemover.removeTeamMembersFromGroup(accessGroup, removalUserIds, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe accessGroup.clients
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
      }
    }
  }

  trait TestScope {

    val mockAuditService: AuditService = mock[AuditService]

    val groupTeamMembersRemover = new GroupTeamMembersRemoverImpl(mockAuditService)

    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName1")
    val agentUser2: AgentUser = AgentUser("userId2", "userName2")
    val now: LocalDateTime = LocalDateTime.now()

    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]]): AccessGroup =
      AccessGroup(
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

    def mockAuditServiceAuditAccessGroupTeamMembersRemoval()
      : CallHandler4[AccessGroup, Set[AgentUser], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupTeamMembersRemoval(_: AccessGroup, _: Set[AgentUser])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

  }

}
