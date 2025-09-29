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

package it.controllers

import play.api.libs.json._
import play.api.test.Helpers._
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.model._
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client}
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, TaxGroupsRepositoryV2}

import java.util.UUID

class AccessGroupsControllerISpec extends ComponentBaseISpec {

  val customGroupRepo: CustomGroupsRepositoryV2 = inject[CustomGroupsRepositoryV2]
  val taxGroupRepo: TaxGroupsRepositoryV2 = inject[TaxGroupsRepositoryV2]

  val accessGroupNameCheckUrl: String = s"$baseUrl/arn/${arn.value}/access-group-name-check"
  val getGroupSummariesForClientUrl: String = s"$baseUrl/arn/${arn.value}/client/${c1.enrolmentKey}/groups"
  val getGroupSummariesForTeamMemberUrl: String = s"$baseUrl/arn/${arn.value}/team-member/${tm1.id}/groups"
  val getAllGroupSummariesUrl: String = s"$baseUrl/arn/${arn.value}/all-groups"
  val groupsUrl: String = s"$baseUrl/arn/${arn.value}/groups"
  def groupUrl(gid: String = ":gid"): String = s"$baseUrl/groups/$gid"
  def getCustomGroupUrl(gid: String = ":gid"): String = s"$baseUrl/custom-group/$gid"
  def addMembersToGroupUrl(gid: String = ":gid"): String = s"$baseUrl/groups/$gid/members/add"
  def paginatedClientsToGroupUrl(gid: String = ":gid"): String = s"$baseUrl/group/$gid/clients"
  def paginatedClientsAddingToGroupUrl(gid: String = ":gid"): String = s"$baseUrl/group/$gid/clients/add"
  def removeClientUrl(gid: String = ":gid", userId: String = ":id1"): String = s"$baseUrl/groups/$gid/clients/$userId"
  def removeTeamMemberUrl(gid: String = ":gid", userId: String = ":id1"): String =
    s"$baseUrl/groups/$gid/members/$userId"
  val syncWithEacdUrl: String = s"$baseUrl/arn/${arn.value}/sync"
  val unassignedClientsUrl: String = s"$baseUrl/arn/${arn.value}/unassigned-clients"

  s"GET $accessGroupNameCheckUrl" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)
      givenGetClientsSuccess(arn)

      await(customGroupRepo.insert(customGroup))

      val result = get(s"$accessGroupNameCheckUrl?name=ag1")

      result.status shouldBe OK
    }

    s"return $CONFLICT" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)
      givenGetClientsSuccess(arn)

      await(customGroupRepo.insert(customGroup))

      val result = get(s"$accessGroupNameCheckUrl?name=Group+1")

      result.status shouldBe CONFLICT
    }
  }

  s"GET $getAllGroupSummariesUrl" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup))

      val result = get(s"$getAllGroupSummariesUrl")

      result.status shouldBe OK

      result.body should include(""""groupName":"Group 1","clientCount":2,"teamMemberCount":2""")
    }
  }

  s"GET $getGroupSummariesForTeamMemberUrl" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup))

      val result = get(s"$getGroupSummariesForTeamMemberUrl")

      result.status shouldBe OK

      result.body should include(""""groupName":"Group 1","clientCount":2,"teamMemberCount":2""")
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(s"$getGroupSummariesForTeamMemberUrl")

      result.status shouldBe NOT_FOUND
    }
  }

  s"GET $getGroupSummariesForClientUrl" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup))

      await(customGroupRepo.insert(customGroup))

      val result = get(s"$getGroupSummariesForClientUrl")

      result.status shouldBe OK

      result.body should include(""""groupName":"Group 1","clientCount":2,"teamMemberCount":2""")
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(s"$getGroupSummariesForClientUrl")

      result.status shouldBe NOT_FOUND
    }
  }

  s"POST $groupsUrl" should {

    s"return $CREATED after custom access group created" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set(UserEnrolment(userId = "id3", enrolmentKey = "HMRC-MTD-VAT~VRN~123456788")),
          unassign = Set.empty,
          arn
        )
      )

      val result = post(groupsUrl)(
        Json.toJson(
          CreateAccessGroupRequest(
            groupName = "new",
            teamMembers = Some(Set(AgentUser("id3", "tm3"))),
            clients = Some(Set(Client("HMRC-MTD-VAT~VRN~123456788", "vat3")))
          )
        )
      )

      val createdGroupId = await(customGroupRepo.get(arn)).head.id

      result.status shouldBe CREATED

      result.body should include(s"$createdGroupId")
    }

    s"return $CREATED after custom access group created without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(customGroupRepo.insert(customGroup))

      val result = post(groupsUrl)(
        Json.toJson(
          CreateAccessGroupRequest(groupName = "new", teamMembers = Some(teamMembers), clients = Some(clients))
        )
      )

      val createdGroupId = await(customGroupRepo.get(arn)).last.id

      result.status shouldBe CREATED

      result.body should include(s""""$createdGroupId"""")
    }

    s"return $CONFLICT when the name already exists" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(customGroupRepo.insert(customGroup))

      val result = post(groupsUrl)(
        Json.toJson(CreateAccessGroupRequest(groupName = "group 1", teamMembers = None, clients = None))
      )

      result.status shouldBe CONFLICT

    }

    s"return $BAD_REQUEST if name length is too long" in {

      givenAuthorisedAsAgentWith(arn.value)

      val name = List.fill(51)("o")

      val result =
        post(groupsUrl)(Json.toJson(CreateAccessGroupRequest(groupName = s"$name", teamMembers = None, clients = None)))

      result.status shouldBe BAD_REQUEST

      result.body should include("""{"message":"Group name length exceeds maximum allowed 50"}""")
    }

    s"return $BAD_REQUEST if payload is invalid" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result =
        post(groupsUrl)(Json.parse("""{"invalid":"body"}"""))

      result.status shouldBe BAD_REQUEST

      result.body should include("""{"message":{"obj.groupName":[{"msg":["error.path.missing"],"args":[]}]}}""")
    }
  }

  s"GET $groupsUrl" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup))

      val result = get(groupsUrl)

      result.status shouldBe OK

      result.body should include(""""groupName":"Group 1","clientCount":2,"teamMemberCount":2""")
    }
  }

  s"GET ${groupUrl()}" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(groupUrl(gid))

      result.status shouldBe OK

      val body = Json.parse(result.body)
      (body \ "id").as[String] shouldBe gid
      (body \ "groupName").as[String] shouldBe "Group 1"

    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(groupUrl(UUID.randomUUID().toString))

      result.status shouldBe NOT_FOUND
    }

    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("DARN00000012")
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(groupUrl(gid))

      result.status shouldBe FORBIDDEN
    }
  }

  s"GET ${getCustomGroupUrl()}" should {

    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(getCustomGroupUrl(gid))

      result.status shouldBe OK

      val body = Json.parse(result.body)
      (body \ "groupId").as[String] shouldBe gid
      (body \ "groupName").as[String] shouldBe "Group 1"
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(groupUrl(UUID.randomUUID().toString))

      result.status shouldBe NOT_FOUND
    }

    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("DARN00000012")
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(groupUrl(gid))

      result.status shouldBe FORBIDDEN

      result.body.isEmpty shouldBe true
    }
  }

  s"DELETE ${groupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get
      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set.empty,
          unassign = Set(
            UserEnrolment(userId = tm1.id, enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = tm1.id, enrolmentKey = c2.enrolmentKey),
            UserEnrolment(userId = tm2.id, enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = tm2.id, enrolmentKey = c2.enrolmentKey)
          ),
          arn
        )
      )

      val result = delete(groupUrl(gid))

      result.status shouldBe OK
    }

    s"return $OK without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup.copy(id = UUID.randomUUID(), groupName = "easy")))
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = delete(groupUrl(gid))

      result.status shouldBe OK
    }

    s"return $NOT_MODIFIED" in {
      // refactor to remove this result
    }
  }

  s"PATCH ${groupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set.empty,
          unassign = Set(
            UserEnrolment(userId = "id1", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id1", enrolmentKey = c2.enrolmentKey),
            UserEnrolment(userId = "id2", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id2", enrolmentKey = c2.enrolmentKey)
          ),
          arn
        )
      )
      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set(
            UserEnrolment(userId = "id3", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id3", enrolmentKey = c2.enrolmentKey)
          ),
          unassign = Set.empty,
          arn
        )
      )

      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = patch(groupUrl(gid))(
        Json.toJson(
          UpdateAccessGroupRequest(
            groupName = None,
            teamMembers = Some(Set(AgentUser(id = "id3", name = "tm3"))),
            clients = None
          )
        )
      )

      result.status shouldBe OK
    }

    s"return $OK without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = patch(groupUrl(gid))(
        Json.toJson(UpdateAccessGroupRequest(groupName = Some("new name"), teamMembers = None, clients = None))
      )

      result.status shouldBe OK

    }
    s"return $NOT_FOUND" in {
      // todo refactor endpoint so the group not found case is returned as a 404 rather than a 400.
    }
    s"return $BAD_REQUEST" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = patch(groupUrl(UUID.randomUUID().toString))(
        Json.toJson(UpdateAccessGroupRequest(groupName = Some("new name"), teamMembers = None, clients = None))
      )

      result.status shouldBe BAD_REQUEST
    }
  }

  s"PUT ${addMembersToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set(
            UserEnrolment(userId = "id3", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id3", enrolmentKey = c2.enrolmentKey)
          ),
          unassign = Set.empty,
          arn
        )
      )

      val result = put(addMembersToGroupUrl(gid))(
        Json.toJson(AddMembersToAccessGroupRequest(teamMembers = Some(Set(AgentUser("id3", "tm3"))), clients = None))
      )

      result.status shouldBe OK
    }
    s"return $OK without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = put(addMembersToGroupUrl(gid))(
        Json.toJson(AddMembersToAccessGroupRequest(teamMembers = None, clients = None))
      )

      result.status shouldBe OK
    }
    s"return $NOT_FOUND" in {
      // todo refactor endpoint so the group not found case is returned as a 404 rather than a 400.
    }
  }

  s"PATCH ${addMembersToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set(
            UserEnrolment(userId = "id3", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id3", enrolmentKey = c2.enrolmentKey)
          ),
          unassign = Set.empty,
          arn
        )
      )

      val result = patch(addMembersToGroupUrl(gid))(
        Json.toJson(AddOneTeamMemberToGroupRequest(teamMember = AgentUser("id3", "tm3")))
      )

      result.status shouldBe OK
    }
    s"return $OK without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get
      await(
        customGroupRepo.insert(
          customGroup.copy(
            id = UUID.randomUUID(),
            groupName = "other group",
            teamMembers = customGroup.teamMembers + AgentUser("id3", "tm3")
          )
        )
      )

      val result = patch(addMembersToGroupUrl(gid))(
        Json.toJson(AddOneTeamMemberToGroupRequest(teamMember = AgentUser("id3", "tm3")))
      )

      result.status shouldBe OK
    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = patch(addMembersToGroupUrl(UUID.randomUUID().toString))(
        Json.toJson(AddOneTeamMemberToGroupRequest(teamMember = AgentUser("id3", "tm3")))
      )

      result.status shouldBe NOT_FOUND
    }
  }

  s"GET ${paginatedClientsToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(s"${paginatedClientsToGroupUrl(gid)}?page=1&pageSize=10")

      result.status shouldBe OK

      val body = Json.parse(result.body).as[PaginatedList[Client]]

      body.pageContent shouldBe Seq(c2, c1)

    }
    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("HARN1155367")
      givenGetClientsSuccess(arn)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(s"${paginatedClientsToGroupUrl(gid)}?page=1&pageSize=10")

      result.status shouldBe FORBIDDEN
    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(s"${paginatedClientsToGroupUrl(UUID.randomUUID().toString)}?page=1&pageSize=10")

      result.status shouldBe NOT_FOUND
    }
  }

  s"GET ${paginatedClientsAddingToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      givenGetPaginatedClientsSuccess(arn)(Seq(c1, c2))
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      val result = get(s"${paginatedClientsAddingToGroupUrl(gid)}?page=1&pageSize=10")

      result.status shouldBe OK
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(s"${paginatedClientsAddingToGroupUrl(UUID.randomUUID().toString)}?page=1&pageSize=10")

      result.status shouldBe NOT_FOUND
    }
  }

  s"DELETE ${removeClientUrl()}" should {
    s"return $NO_CONTENT" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set().empty,
          unassign = Set(
            UserEnrolment(userId = "id1", enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = "id2", enrolmentKey = c1.enrolmentKey)
          ),
          arn
        )
      )

      val result = delete(removeClientUrl(gid, "HMRC-MTD-VAT~VRN~123456789"))

      result.status shouldBe NO_CONTENT

    }
    s"return $NO_CONTENT without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      await(customGroupRepo.insert(customGroup.copy(id = UUID.randomUUID(), groupName = "other"))).get

      val result = delete(removeClientUrl(gid, "HMRC-MTD-VAT~VRN~123456789"))

      result.status shouldBe NO_CONTENT

    }
    s"return $NOT_MODIFIED" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = delete(removeClientUrl(UUID.randomUUID().toString, "HMRC-MTD-VAT~VRN~123456789"))

      result.status shouldBe NOT_MODIFIED
    }
  }
  s"DELETE ${removeTeamMemberUrl()}" should {
    s"return $NO_CONTENT" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      givenPushUserAssignmentSuccess(
        UserEnrolmentAssignments(
          assign = Set().empty,
          unassign = Set(
            UserEnrolment(userId = tm1.id, enrolmentKey = c1.enrolmentKey),
            UserEnrolment(userId = tm1.id, enrolmentKey = c2.enrolmentKey)
          ),
          arn
        )
      )

      val result = delete(removeTeamMemberUrl(gid, tm1.id))

      result.status shouldBe NO_CONTENT
    }

    s"return $NO_CONTENT without assignments pushed" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid: String = await(customGroupRepo.insert(customGroup)).get

      await(customGroupRepo.insert(customGroup.copy(id = UUID.randomUUID(), groupName = "other"))).get

      val result = delete(removeTeamMemberUrl(gid, tm1.id))

      result.status shouldBe NO_CONTENT
    }

    s"return $NOT_MODIFIED" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = delete(removeTeamMemberUrl(UUID.randomUUID().toString, tm1.id))

      result.status shouldBe NOT_MODIFIED
    }
  }

  s"PATCH $syncWithEacdUrl" should {
    s"return $ACCEPTED" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(customGroupRepo.insert(customGroup)).get
      givenOutstandingAssignmentsWorkItems(arn)

      val result = patch(syncWithEacdUrl)(JsNull)

      result.status shouldBe ACCEPTED
    }
  }

  s"POST $syncWithEacdUrl" should {
    s"return $ACCEPTED" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(customGroupRepo.insert(customGroup))
      givenOutstandingAssignmentsWorkItems(arn)

      val result = post(syncWithEacdUrl)(JsNull)

      result.status shouldBe ACCEPTED
    }
  }

  s"GET $unassignedClientsUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      await(customGroupRepo.insert(customGroup))

      val result = get(unassignedClientsUrl)

      result.status shouldBe OK
    }
  }
}
