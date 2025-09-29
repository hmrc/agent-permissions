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

package it.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.model.accessgroups.{Client, UserDetails}
import uk.gov.hmrc.agentpermissions.model.{Arn, PaginatedList, PaginationMetaData, UserEnrolmentAssignments}

trait AgentUserClientDetailsStubs {
  _: TestConstants =>

  private val aucdPath: String = "/agent-user-client-details"

  def givenAgentSizeSuccess(arn: Arn): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/agent-size"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{"client-count": 10}"""))
    )

  def givenAgentSizeFails(arn: Arn)(status: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/agent-size"))
        .willReturn(aResponse().withStatus(status))
    )

  def givenClientCountByTaxServiceSuccess(arn: Arn, body: Map[String, Int] = expectedCount): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/tax-service-client-count"))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(body).toString()))
    )

  def givenClientCountByTaxServiceFails(arn: Arn)(status: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/tax-service-client-count"))
        .willReturn(aResponse().withStatus(status))
    )

  def givenUserCheck(arn: Arn, statusResponse: Int = 204): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/user-check")).willReturn(aResponse().withStatus(statusResponse))
    )

  def givenOutstandingWorkItems(arn: Arn, statusResponse: Int = 200): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/work-items-exist")).willReturn(aResponse().withStatus(statusResponse))
    )

  def givenOutstandingAssignmentsWorkItems(arn: Arn, statusResponse: Int = 200): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/assignments-work-items-exist"))
        .willReturn(aResponse().withStatus(statusResponse))
    )

  def givenGetClientsSuccess(
    arn: Arn,
    status: Int = 200,
    clients: Seq[Client] = Seq(c1, c2),
    sendEmail: Boolean = false,
    lang: Option[String] = None
  ): StubMapping = {
    val params = if (sendEmail) "?sendEmail=true" + lang.fold("")("&lang=" + _) else ""
    val uri = s"/client-list$params"
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}$uri")).willReturn(
        aResponse()
          .withStatus(status)
          .withBody(Json.toJson(clients).toString())
      )
    )
  }

  def givenGetClientsFails(arn: Arn)(status: Int): StubMapping =
    stubFor(get(urlEqualTo(s"$aucdPath/arn/${arn.value}/client-list")).willReturn(aResponse().withStatus(status)))

  def givenPushUserAssignmentSuccess(userAssignments: UserEnrolmentAssignments): StubMapping =
    stubFor(
      post(urlEqualTo(s"$aucdPath/user-enrolment-assignments"))
        .withRequestBody(equalToJson(Json.toJson(userAssignments).toString()))
        .willReturn(aResponse().withStatus(202))
    )

  def givenPushUserAssignmentFails(userAssignments: UserEnrolmentAssignments, response: Int): StubMapping =
    stubFor(
      post(urlEqualTo(s"$aucdPath/user-enrolment-assignments"))
        .withRequestBody(equalToJson(Json.toJson(userAssignments).toString()))
        .willReturn(aResponse().withStatus(response))
    )

  def givenGetPaginatedClientsSuccess(
    arn: Arn
  )(clients: Seq[Client]): StubMapping = {
    val url = s"$aucdPath/arn/${arn.value}/clients" +
      s"?page=1&pageSize=10"
    stubFor(
      get(urlEqualTo(url)).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            Json
              .toJson(PaginatedList(pageContent = clients, PaginationMetaData(false, true, 2, 1, 10, 1, 2, None)))
              .toString()
          )
      )
    )
  }

  def givenGetTeamMembersSuccess(arn: Arn)(teamMembers: Seq[UserDetails]): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/team-members")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(Json.toJson(teamMembers).toString())
      )
    )

  def givenGetTeamMembersFails(arn: Arn): StubMapping =
    stubFor(get(urlEqualTo(s"$aucdPath/arn/${arn.value}/team-members")).willReturn(aResponse().withStatus(500)))

  def givenGetClientListStatusReturns(arn: Arn, status: Int = 202): StubMapping =
    stubFor(
      get(urlEqualTo(s"$aucdPath/arn/${arn.value}/client-list-status")).willReturn(aResponse().withStatus(status))
    )

  def givenSyncTeamMemberReturns(arn: Arn, userId: String = "id-1")(status: Int): StubMapping =
    stubFor(
      post(urlEqualTo(s"$aucdPath/arn/${arn.value}/user/$userId/ensure-assignments"))
        .willReturn(aResponse().withStatus(status))
    )

}
