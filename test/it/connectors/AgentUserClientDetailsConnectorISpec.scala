/*
 * Copyright 2025 HM Revenue & Customs
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

package it.connectors

import it.stubs.AgentUserClientDetailsStubs
import play.api.http.Status.OK
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.connectors.AgentUserClientDetailsConnectorImpl
import uk.gov.hmrc.agentpermissions.model.EacdAssignmentsPushStatus.{AssignmentsNotPushed, AssignmentsPushed}
import uk.gov.hmrc.agentpermissions.model.accessgroups.{Client, UserDetails}
import uk.gov.hmrc.agentpermissions.model.{PaginatedList, PaginationMetaData, UserEnrolmentAssignments}
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

class AgentUserClientDetailsConnectorISpec extends ComponentBaseISpec with AgentUserClientDetailsStubs {

  private val connector = inject[AgentUserClientDetailsConnectorImpl]

  "agentSize" when {

    s"http response has 200 status code" should {
      "return the client count" in {

        givenAgentSizeSuccess(arn)

        val result = connector.agentSize(arn).futureValue

        result shouldBe Some(10)
      }
    }

    "http response has non-200 status codes" should {
      "return None" in {

        givenAgentSizeFails(arn)(500)

        val result = connector.agentSize(arn).futureValue

        result shouldBe None
      }
    }
  }

  "clientCountByTaxService" when {

    s"http response has 200 status code" should {
      "return the client count by tax service" in {

        val body = Map("HMRC-MTD-IT" -> 20)

        givenClientCountByTaxServiceSuccess(arn, body)

        val result = connector.clientCountByTaxService(arn).futureValue

        result shouldBe Some(body)

      }
    }

    "http response has non-200 status codes" should {

      s"return nothing for any error" in {

        givenClientCountByTaxServiceFails(arn)(500)

        val result = connector.clientCountByTaxService(arn).futureValue

        result shouldBe None
      }
    }
  }

  "isSingleUserAgency" when {

    s"http response has 204 status code" should {
      "return false" in {

        givenUserCheck(arn)

        val result = connector.isSingleUserAgency(arn).futureValue

        result shouldBe Some(false)
      }
    }

    s"http response has 403 status code" should {
      "return true" in {

        givenUserCheck(arn, 403)

        val result = connector.isSingleUserAgency(arn).futureValue

        result shouldBe Some(true)
      }
    }

    "http response has 5xx status code" should {
      s"return nothing for 500" in {

        givenUserCheck(arn, 500)

        val result = connector.isSingleUserAgency(arn).futureValue

        result shouldBe None
      }
    }
  }

  "outstandingWorkItemsExist" when {

    s"http response has 200 status code" should {
      "return true" in {

        givenOutstandingWorkItems(arn)

        val result = connector.outstandingWorkItemsExist(arn).futureValue

        result shouldBe Some(true)
      }
    }

    s"http response has 204 status code" should {
      "return false" in {

        givenOutstandingWorkItems(arn, 204)

        val result = connector.outstandingWorkItemsExist(arn).futureValue

        result shouldBe Some(false)

      }
    }

    "http response has error status codes" should {
      s"return nothing for 500" in {

        givenOutstandingWorkItems(arn, 500)

        val result = connector.outstandingWorkItemsExist(arn).futureValue

        result shouldBe None
      }

    }
  }

  "outstandingAssignmentsWorkItemsExist" when {

    s"http response has 200 status code" should {
      "return true" in {

        givenOutstandingAssignmentsWorkItems(arn)

        val result = connector.outstandingAssignmentsWorkItemsExist(arn).futureValue

        result shouldBe Some(true)

      }
    }

    s"http response has 204 status code" should {
      "return false" in {

        givenOutstandingAssignmentsWorkItems(arn, 204)

        val result = connector.outstandingAssignmentsWorkItemsExist(arn).futureValue

        result shouldBe Some(false)
      }
    }

    "http response has error status codes" should {
      s"return nothing for 500" in {

        givenOutstandingAssignmentsWorkItems(arn, 500)

        val result = connector.outstandingAssignmentsWorkItemsExist(arn).futureValue

        result shouldBe None
      }
    }
  }

  "getClients" when {

    s"http response has 202 status code and to send email" should {
      "return some value" in {

        val clients = Seq(Client("HMRC-MTD-VAT~VRN~123456789", "abc"))

        givenGetClientsSuccess(arn, OK, clients, sendEmail = true, lang = Some("en"))

        val result = connector.getClients(arn, sendEmail = true, lang = Some("en")).futureValue

        result shouldBe Some(clients)
      }
    }

    s"http response has 200 status code" should {
      "return some value" in {

        val clients = Seq(Client("HMRC-MTD-VAT~VRN~123456789", "abc"))

        givenGetClientsSuccess(arn, 202, clients)

        val result = connector.getClients(arn).futureValue

        result shouldBe Some(clients)

      }
    }

    "http response has 4xx status codes" should {
      s"return nothing for 404" in {

        givenGetClientsFails(arn)(404)

        val result = connector.getClients(arn).futureValue

        result shouldBe None
      }
    }

    "http response has 5xx status codes" should {
      s"throw upstream exception nothing for 500" in {

        givenGetClientsFails(arn)(500)

        intercept[UpstreamErrorResponse] {
          await(connector.getClients(arn))
        }
      }
    }

    "getPaginatedClientsList" should {

      "return a PaginatedList[Client] when status response is OK" in {

        givenGetPaginatedClientsSuccess(arn)(Seq(c1, c2))

        val result = connector.getPaginatedClients(arn)(page = 1, pageSize = 10).futureValue

        result shouldBe PaginatedList(List(c1, c2), PaginationMetaData(false, true, 2, 1, 10, 1, 2, None))
      }
    }

  }

  "getClientListStatus" when {

    s"http response has 202 status code" should {
      s"return 202" in {

        givenGetClientListStatusReturns(arn)

        val result = connector.getClientListStatus(arn).futureValue

        result shouldBe Some(202)
      }
    }

    s"http response has 200 status code" should {
      s"return 200" in {

        givenGetClientListStatusReturns(arn, 200)

        val result = connector.getClientListStatus(arn).futureValue

        result shouldBe Some(200)

      }
    }

    "http response has 4xx status codes" should {
      s"return nothing for 404" in {

        givenGetClientListStatusReturns(arn, 404)

        val result = connector.getClientListStatus(arn).futureValue

        result shouldBe None

      }
    }

    "http response has 5xx status codes" should {
      s"throw upstream exception nothing for 500" in {

        givenGetClientListStatusReturns(arn, 500)

        intercept[UpstreamErrorResponse] {
          await(connector.getClientListStatus(arn))
        }
      }
    }
  }

  "push calculated assignments" when {

    s"http response has 202 status code" should {
      s"return AssignmentsPushed" in {

        val body = UserEnrolmentAssignments(assign = Set.empty, unassign = Set.empty, arn = arn)
        givenPushUserAssignmentSuccess(body)

        val result = connector.pushAssignments(body).futureValue

        result shouldBe AssignmentsPushed
      }
    }

    "http response has non-200 status codes" should {
      s"return nothing for 500" in {

        val body = UserEnrolmentAssignments(assign = Set.empty, unassign = Set.empty, arn = arn)
        givenPushUserAssignmentFails(body, 500)

        val result = connector.pushAssignments(body).futureValue

        result shouldBe AssignmentsNotPushed
      }
    }
  }

  "getClientsWithAssignedUsers" when {

    "getTeamMembers" when {

      s"http response has 200 status code" should {
        "return some value" in {

          val teamMembers = Seq(UserDetails(userId = Some("id-1")))

          givenGetTeamMembersSuccess(arn)(teamMembers)

          val result = connector.getTeamMembers(arn).futureValue

          result shouldBe teamMembers

        }
      }

      "http response has non-200 status codes" should {
        s"fail for 500" in {

          givenGetTeamMembersFails(arn)

          intercept[UpstreamErrorResponse] {
            await(connector.getTeamMembers(arn))
          }
        }
      }
    }

    "syncTeamMember" when {
      // val userId = "myUser"

      s"http response has 200 status code" should {
        "return false" in {

          givenSyncTeamMemberReturns(arn)(200)

          val result = connector.syncTeamMember(arn, "id-1", List(c1.enrolmentKey)).futureValue

          result shouldBe false
        }
      }

      s"http response has 202 status code" should {
        "return true" in {

          givenSyncTeamMemberReturns(arn)(202)

          val result = connector.syncTeamMember(arn, "id-1", List(c1.enrolmentKey)).futureValue

          result shouldBe true
        }
      }

      "http response has non-successful status codes" should {

        s"fail for 500" in {

          givenSyncTeamMemberReturns(arn)(500)

          intercept[HttpException] {
            await(connector.syncTeamMember(arn, "id-1", List(c1.enrolmentKey)))
          }
        }
      }
    }
  }
}
