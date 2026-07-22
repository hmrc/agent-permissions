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

import play.api.mvc.Request
import play.api.test.FakeRequest
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.controllers.{AuthAction, AuthorisedAgent}
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser

class AuthActionISpec extends ComponentBaseISpec {

  val authAction = inject[AuthAction]

  "getAuthorisedAgent" should {
    "return Some[AuthorisedAgent] for a User" in {

      given Request[?] = FakeRequest().withHeaders("Authorization" -> "Bearer xyz")

      givenAuthorisedAsAgentWith(arn.value)
      val result = authAction.getAuthorisedAgent(allowStandardUser = true, allowlistEnabled = false).futureValue

      result shouldBe Some(AuthorisedAgent(arn, AgentUser("id-1", "")))
    }

    "return Some[AuthorisedAgent] for a Standard User" in {

      given Request[?] = FakeRequest().withHeaders("Authorization" -> "Bearer xyz")

      givenAuthorisedAsAgentWith(arn.value, isAdmin = false)
      val result = authAction.getAuthorisedAgent(allowStandardUser = true, allowlistEnabled = false).futureValue

      result shouldBe Some(AuthorisedAgent(arn, AgentUser("id-1", "")))
    }

    "return None for a Standard User when Standard Users are not allowed" in {

      given Request[?] = FakeRequest().withHeaders("Authorization" -> "Bearer xyz")

      givenAuthorisedAsAgentWith(arn.value, isAdmin = false)
      val result = authAction.getAuthorisedAgent(allowlistEnabled = false).futureValue

      result shouldBe None
    }

    "return None for a User when they are not on the allow list and the allow list is enabled" in {

      given Request[?] = FakeRequest().withHeaders("Authorization" -> "Bearer xyz")

      givenAuthorisedAsAgentWith("RARN8603525")
      val result = authAction.getAuthorisedAgent().futureValue

      result shouldBe None
    }

    "return None for a legacy agent" in {

      given Request[?] = FakeRequest().withHeaders("Authorization" -> "Bearer xyz")

      givenLegacyAgent()
      val result = authAction.getAuthorisedAgent().futureValue

      result shouldBe None
    }

    "return None if the user is not signed in" in {

      given Request[?] = FakeRequest()

      givenIsNotLoggedIn()
      val result = authAction.getAuthorisedAgent().futureValue

      result shouldBe None
    }
  }
}
