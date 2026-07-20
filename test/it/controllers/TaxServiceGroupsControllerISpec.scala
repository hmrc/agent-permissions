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

import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.libs.ws.WSBodyReadables.readableAsString
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.agentpermissions.model.{AddMembersToTaxServiceGroupRequest, AddOneTeamMemberToGroupRequest, CreateTaxServiceGroupRequest, UpdateTaxServiceGroupRequest}
import uk.gov.hmrc.agentpermissions.repository.TaxGroupsRepositoryV2

import java.util.UUID

class TaxServiceGroupsControllerISpec extends ComponentBaseISpec {

  val createTaxGroupUrl: String = s"$baseUrl/arn/${arn.value}/tax-group"
  val getTaxGroupsUrl: String = s"$baseUrl/arn/${arn.value}/tax-groups"
  val getTaxGroupByServiceUrl: String = s"$baseUrl/arn/${arn.value}/tax-group/HMRC-MTD-VAT"
  def taxGroupUrl(gid: String = ":gid"): String = s"$baseUrl/tax-group/$gid"
  def addTeamMemberToGroupUrl(gid: String = ":gid"): String = s"$baseUrl/tax-group/$gid/members/add"
  def removeTeamMemberUrl(gid: String = ":gid", teamMember: String = s"${tm1.id}"): String =
    s"$baseUrl/tax-group/$gid/members/$teamMember"
  val clientCountForAvailableTaxServicesUrl: String = s"$baseUrl/arn/${arn.value}/client-count/available-tax-services"
  val clientCountForTaxGroupsUrl: String = s"$baseUrl/arn/${arn.value}/client-count/tax-groups"

  private val createTaxServiceGroupRequest: CreateTaxServiceGroupRequest =
    CreateTaxServiceGroupRequest(groupName = "Group 1", teamMembers = Some(teamMembers), service = "HMRC-MTD-VAT")

  private val repo = inject[TaxGroupsRepositoryV2]

  s"POST $createTaxGroupUrl" should {
    s"return $CREATED" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = post(createTaxGroupUrl)(Json.toJson(createTaxServiceGroupRequest))

      result.status shouldBe CREATED
    }

    s"return $CONFLICT" in {

      givenAuthorisedAsAgentWith(arn.value)

      await(repo.insert(taxGroup))

      val result = post(createTaxGroupUrl)(Json.toJson(createTaxServiceGroupRequest))

      result.status shouldBe CONFLICT
    }

    s"return $BAD_REQUEST when name is too long" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result =
        post(createTaxGroupUrl)(Json.toJson(createTaxServiceGroupRequest.copy(groupName = List.fill(51)("a").mkString)))

      result.status shouldBe BAD_REQUEST
      result.body should include("""{"message":"Group name length exceeds maximum allowed 50"}""")

    }

    s"return $BAD_REQUEST when payload is invalid" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result =
        post(createTaxGroupUrl)(Json.parse("""{"invalid":"payload"}"""))

      result.status shouldBe BAD_REQUEST
      result.body should include(
        """{"message":{"obj.service":[{"msg":["error.path.missing"],"args":[]}],"obj.groupName":[{"msg":["error.path.missing"],"args":[]}]}}"""
      )
    }

    s"return $BAD_REQUEST when arn is invalid" in {

      givenAuthorisedAsAgentWith("HARN0001")

      val result = post(s"$baseUrl/arn/HARN0001/tax-group")(Json.toJson(createTaxServiceGroupRequest))

      result.status shouldBe BAD_REQUEST
      result.body should include("""{"message":"Invalid arn value: 'HARN0001' provided"}""")
    }

    s"return $INTERNAL_SERVER_ERROR when tax service group could not be created" in {

      // todo remove this
    }
    s"return $INTERNAL_SERVER_ERROR when some other error" in {

      // todo do we need this?
    }
  }

  s"GET $getTaxGroupsUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.insert(taxGroup))
      val result = get(getTaxGroupsUrl)

      result.status shouldBe OK

      val body = Json.parse(result.body).head

      (body \ "groupName").as[String] shouldBe "Group 1"
      (body \ "teamMemberCount").as[Int] shouldBe 2
      (body \ "taxService").as[String] shouldBe "HMRC-MTD-VAT"
    }
  }

  s"GET $getTaxGroupByServiceUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.insert(taxGroup))

      val result = get(getTaxGroupByServiceUrl)

      result.status shouldBe OK

      val body = Json.parse(result.body)

      (body \ "groupName").as[String] shouldBe "Group 1"
      (body \ "teamMembers").as[Set[AgentUser]] shouldBe Set(tm1, tm2)
      (body \ "service").as[String] shouldBe "HMRC-MTD-VAT"

    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(getTaxGroupByServiceUrl)

      result.status shouldBe NOT_FOUND
    }
    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("DARN00000012")

      val result = get(getTaxGroupByServiceUrl)

      result.status shouldBe FORBIDDEN
    }
  }

  s"GET ${taxGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result = get(taxGroupUrl(gid))

      result.status shouldBe OK

      val body = Json.parse(result.body)

      (body \ "groupName").as[String] shouldBe "Group 1"
      (body \ "teamMembers").as[Set[AgentUser]] shouldBe Set(tm1, tm2)
    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(taxGroupUrl(UUID.randomUUID().toString))

      result.status shouldBe NOT_FOUND
    }
    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("DARN00000012")
      val gid = await(repo.insert(taxGroup)).get

      val result = get(taxGroupUrl(gid))

      result.status shouldBe FORBIDDEN
    }
  }

  s"PATCH ${taxGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result =
        patch(taxGroupUrl(gid))(Json.toJson(UpdateTaxServiceGroupRequest(groupName = Some("new"), teamMembers = None)))

      result.status shouldBe OK
    }
    s"return $NOT_FOUND" in {

      // todo refactor the 400 (not found) to be 404.

    }
    s"return $BAD_REQUEST" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result = patch(taxGroupUrl(gid))(
        Json.toJson(UpdateTaxServiceGroupRequest(groupName = Some(List.fill(51)("a").mkString), teamMembers = None))
      )

      result.status shouldBe BAD_REQUEST
    }
    s"return $INTERNAL_SERVER_ERROR" in {
      // todo refactor to remove this.
    }
  }

  s"DELETE ${taxGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result = delete(taxGroupUrl(gid))

      result.status shouldBe OK

    }
    s"return $NOT_MODIFIED" in {

      // todo not needed.
    }
  }

  s"PATCH ${addTeamMemberToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result =
        patch(addTeamMemberToGroupUrl(gid))(
          Json.toJson(
            AddOneTeamMemberToGroupRequest(teamMember = AgentUser("id3", "tm3"))
          )
        )

      result.status shouldBe OK
    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result =
        patch(addTeamMemberToGroupUrl(UUID.randomUUID().toString))(
          Json.toJson(
            AddOneTeamMemberToGroupRequest(teamMember = AgentUser("id3", "tm3"))
          )
        )

      result.status shouldBe NOT_FOUND
    }
  }

  s"PUT ${addTeamMemberToGroupUrl()}" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result =
        put(addTeamMemberToGroupUrl(gid))(
          Json.toJson(
            AddMembersToTaxServiceGroupRequest(teamMembers = Some(Set(AgentUser("id3", "tm3"))), excludedClients = None)
          )
        )

      result.status shouldBe OK

    }

    s"return $BAD_REQUEST" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result =
        put(addTeamMemberToGroupUrl(UUID.randomUUID().toString))(
          Json.toJson(
            AddMembersToTaxServiceGroupRequest(teamMembers = Some(Set(AgentUser("id3", "tm3"))), excludedClients = None)
          )
        )

      result.status shouldBe BAD_REQUEST

      result.body should include("Check provided gid")

    }
    s"return $FORBIDDEN" in {

      givenAuthorisedAsAgentWith("DARN00000012")
      val gid = await(repo.insert(taxGroup)).get

      val result =
        put(addTeamMemberToGroupUrl(gid))(
          Json.toJson(
            AddMembersToTaxServiceGroupRequest(teamMembers = Some(Set(AgentUser("id3", "tm3"))), excludedClients = None)
          )
        )

      result.status shouldBe FORBIDDEN
    }
    s"return $INTERNAL_SERVER_ERROR" in {}
  }

  s"DELETE ${removeTeamMemberUrl()}" should {
    s"return $NOT_MODIFIED" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = delete(removeTeamMemberUrl(UUID.randomUUID().toString, tm1.id))

      result.status shouldBe NOT_MODIFIED
    }

    s"return $NO_CONTENT" in {

      givenAuthorisedAsAgentWith(arn.value)
      val gid = await(repo.insert(taxGroup)).get

      val result = delete(removeTeamMemberUrl(gid, tm1.id))

      result.status shouldBe NO_CONTENT
    }
  }

  s"GET $clientCountForAvailableTaxServicesUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      val clients = Map("HMRC-MTD-IT" -> 2, "HMRC-MTD-VAT" -> 3)
      givenClientCountByTaxServiceSuccess(arn, clients)

      val result = get(clientCountForAvailableTaxServicesUrl)

      result.status shouldBe OK
    }
  }

  s"GET $clientCountForTaxGroupsUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenClientCountByTaxServiceSuccess(arn)

      val result = get(clientCountForTaxGroupsUrl)

      result.status shouldBe OK
    }
  }
}
