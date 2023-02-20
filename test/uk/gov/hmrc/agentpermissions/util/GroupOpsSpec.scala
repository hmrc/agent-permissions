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

package uk.gov.hmrc.agentpermissions.util

import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, Client, CustomGroup}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class GroupOpsSpec extends BaseSpec {

  "Removing clients from access group" when {

    "removal enrolments contain some that exist in access group" should {
      "remove only those matching enrolments of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val removalEnrolmentKeys: Set[String] = Set(clientPpt, clientCgt, clientTrust).map(_.enrolmentKey)

        val (accessGroupWithClientsRemoved, removedClients) =
          GroupOps.removeClientsFromGroup(accessGroup, removalEnrolmentKeys, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe Some(Set(clientVat))
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
        removedClients shouldBe Set(clientPpt, clientCgt)
      }
    }

    "removal enrolments do not contain any that exist in access group" should {
      "not remove any enrolments of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val removalEnrolmentKeys: Set[String] = Set(clientTrust).map(_.enrolmentKey)

        val (accessGroupWithClientsRemoved, removedClients) =
          GroupOps.removeClientsFromGroup(accessGroup, removalEnrolmentKeys, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe accessGroup.clients
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
        removedClients shouldBe Set.empty
      }
    }
  }

  trait TestScope {

    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName1")
    val agentUser2: AgentUser = AgentUser("userId2", "userName2")

    val now: LocalDateTime = LocalDateTime.now()

    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")

    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")

    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val clientTrust: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~0123456789", "Trust Client")

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]]): CustomGroup =
      CustomGroup(
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

  }

  "Removing team members from access group" when {

    "removal user ids contain some that exist in access group" should {
      "remove only those matching user ids of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1, agentUser2)), Some(Set(clientVat)))

        val removalUserIds: Set[String] = Set(agentUser2.id)

        val (accessGroupWithMemberssRemoved, removedMembers) =
          GroupOps.removeTeamMembersFromGroup(accessGroup, removalUserIds, agentUser1)

        accessGroupWithMemberssRemoved.clients shouldBe accessGroup.clients
        accessGroupWithMemberssRemoved.teamMembers shouldBe Some(Set(agentUser1))
        removedMembers shouldBe Set(agentUser2)
      }
    }

    "removal user ids do not contain any that exist in access group" should {
      "not remove any user ids of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1, agentUser2)), Some(Set(clientVat)))

        val removalUserIds: Set[String] = Set("unknown")

        val (accessGroupWithMembersRemoved, removedMembers) =
          GroupOps.removeTeamMembersFromGroup(accessGroup, removalUserIds, agentUser1)

        accessGroupWithMembersRemoved.clients shouldBe accessGroup.clients
        accessGroupWithMembersRemoved.teamMembers shouldBe accessGroup.teamMembers
        removedMembers shouldBe Set.empty
      }
    }
  }

}
