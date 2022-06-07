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

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.auth.core._

import scala.concurrent.ExecutionContext

class AuthActionSpec extends BaseSpec with AuthorisationMockSupport {

  trait TestScope {
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockEnvironment: Environment = mock[Environment]
    val mockConfiguration: Configuration = mock[Configuration]

    val authAction = new AuthAction(mockAuthConnector, mockEnvironment, mockConfiguration)

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  }

  "Getting AuthorisedAgent" when {

    "auth response indicates request is authorised" should {
      "return an authorised agent" in new TestScope {
        mockAuthResponseWithoutException(buildAuthorisedResponse)

        authAction.getAuthorisedAgent().futureValue shouldBe Some(
          AuthorisedAgent(Arn("KARN0762398"), AgentUser("user1", "Jane Doe"))
        )
      }
    }

    "auth response indicates empty enrolments" should {
      "not return an authorised agent" in new TestScope {
        mockAuthResponseWithoutException(buildUnauthorisedResponseHavingEmptyEnrolments)

        authAction.getAuthorisedAgent().futureValue shouldBe None
      }
    }

    "auth response indicates incorrect credential role" should {
      "not return an authorised agent" in new TestScope {
        mockAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentialRole)

        authAction.getAuthorisedAgent().futureValue shouldBe None
      }
    }

    "auth response indicates incorrect username" should {
      "not return an authorised agent" in new TestScope {
        mockAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectUsername)

        authAction.getAuthorisedAgent().futureValue shouldBe None
      }
    }

    "auth response indicates incorrect credentials" should {
      "not return an authorised agent" in new TestScope {
        mockAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentials)

        authAction.getAuthorisedAgent().futureValue shouldBe None
      }
    }

    "auth reponse indicates an exception" should {
      "not return an authorised agent" in new TestScope {
        mockAuthResponseWithException(new RuntimeException("boo boo"))

        authAction.getAuthorisedAgent().futureValue shouldBe None
      }
    }
  }

}
