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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait AuthStubs {

  def givenAuthorisedAsAgentWith(
    arn: String,
    isAdmin: Boolean = true
  ): StubMapping = {
    val credRole =
      if isAdmin then "Admin"
      else "Assistant"
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
                 |  "allEnrolments": [{
                 |    "key": "HMRC-AS-AGENT",
                 |    "identifiers": [{ "key": "AgentReferenceNumber", "value": "$arn" }]
                 |  }],
                 |  "credentialRole": "$credRole",
                 |  "optionalCredentials": {"providerId": "id-1", "providerType": "Government Gateway"}
                 |}""".stripMargin
            )
        )
    )
  }

  def givenFullAuthorisedAsAgentWith(
    arn: String,
    providerId: String,
    isAdmin: Boolean = false,
    email: String = "bob@builder.com"
  ): StubMapping = {
    val credRole =
      if isAdmin then "Admin"
      else "Assistant"
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
                 |  "internalId": "some-id",
                 |  "affinityGroup": "Agent",
                 |  "credentialRole": "$credRole",
                 |  "email": "$email",
                 |  "optionalCredentials": {
                 |    "providerId": "$providerId",
                 |    "providerType": "whatever"
                 |  },
                 |  "optionalName": {
                 |    "name": "Bob",
                 |    "lastName": "The Builder"
                 |  },
                 |  "allEnrolments": [{
                 |    "key": "HMRC-AS-AGENT",
                 |    "identifiers": [{ "key": "AgentReferenceNumber", "value": "$arn" }]
                 |  }]
                 |
                 |}""".stripMargin
            )
        )
    )
  }

  def givenIsNotLoggedIn(): StubMapping =
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(
          aResponse()
            .withHeader("WWW-Authenticate", """MDTP detail="BearerTokenExpired"""")
            .withStatus(401)
        )
    )

  def givenLegacyAgent(): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
                 |  "allEnrolments": [{
                 |    "key": "IR-SA-AGENT",
                 |    "identifiers": [{ "key": "IRAgentReference", "value": "XX1234" }]
                 |  }],
                 |  "credentialRole": "User",
                 |  "optionalCredentials": {"providerId": "id-1", "providerType": "Government Gateway"}
                 |}""".stripMargin
            )
        )
    )
}
