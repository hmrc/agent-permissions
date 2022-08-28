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

package uk.gov.hmrc.agentpermissions.controllers

import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Client}
import uk.gov.hmrc.agentpermissions.BaseSpec

import java.time.LocalDateTime

class UpdateAccessGroupRequestSpec extends BaseSpec {

  val arn: Arn = Arn("KARN0762398")
  val user: AgentUser = AgentUser("userId", "userName")
  val groupName = "some existing group name"
  lazy val now: LocalDateTime = LocalDateTime.now()
  val user1: AgentUser = AgentUser("user1", "User 1")
  val user2: AgentUser = AgentUser("user2", "User 2")
  val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
  val client2: Client = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
  val groupNameToUpdate = "name to update"
  val teamMembersToUpdate: Set[AgentUser] = Set(user1, user2)
  val clientsToUpdate: Set[Client] = Set(client1, client2)
  val accessGroup: AccessGroup = AccessGroup(arn, groupName, now, now, user, user, Some(Set.empty), Some(Set.empty))

  "Merge" when {

    "request has nothing to update" should {
      "return existing access group" in {
        val maybeGroupNameToUpdate: Option[String] = None
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = None
        val maybeClientsToUpdate: Option[Set[Client]] = None

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe accessGroup
      }
    }

    "request has only group name to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = Some(groupNameToUpdate)
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = None
        val maybeClientsToUpdate: Option[Set[Client]] = None

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe accessGroup.copy(groupName = groupNameToUpdate)
      }
    }

    "request has only team members to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = None
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = Some(teamMembersToUpdate)
        val maybeClientsToUpdate: Option[Set[Client]] = None

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe accessGroup.copy(teamMembers = Some(teamMembersToUpdate))
      }
    }

    "request has only clients to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = None
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = None
        val maybeClientsToUpdate: Option[Set[Client]] = Some(clientsToUpdate)

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe accessGroup.copy(clients = Some(clientsToUpdate))
      }
    }

    "request has group name and team members to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = Some(groupNameToUpdate)
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = Some(teamMembersToUpdate)
        val maybeClientsToUpdate: Option[Set[Client]] = None

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe
          accessGroup.copy(groupName = groupNameToUpdate, teamMembers = Some(teamMembersToUpdate))
      }
    }

    "request has group name and clients to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = Some(groupNameToUpdate)
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = None
        val maybeClientsToUpdate: Option[Set[Client]] = Some(clientsToUpdate)

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe
          accessGroup.copy(groupName = groupNameToUpdate, clients = Some(clientsToUpdate))
      }
    }

    "request has team members and clients to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = None
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = Some(teamMembersToUpdate)
        val maybeClientsToUpdate: Option[Set[Client]] = Some(clientsToUpdate)

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe
          accessGroup.copy(teamMembers = Some(teamMembersToUpdate), clients = Some(clientsToUpdate))
      }
    }

    "request has group name, team members and clients to update" should {
      "return merged access group" in {
        val maybeGroupNameToUpdate: Option[String] = Some(groupNameToUpdate)
        val maybeTeamMembersToUpdate: Option[Set[AgentUser]] = Some(teamMembersToUpdate)
        val maybeClientsToUpdate: Option[Set[Client]] = Some(clientsToUpdate)

        val updateAccessGroupRequest =
          UpdateAccessGroupRequest(maybeGroupNameToUpdate, maybeTeamMembersToUpdate, maybeClientsToUpdate)

        updateAccessGroupRequest.merge(accessGroup) shouldBe
          accessGroup.copy(
            groupName = groupNameToUpdate,
            teamMembers = Some(teamMembersToUpdate),
            clients = Some(clientsToUpdate)
          )
      }
    }

  }
}
