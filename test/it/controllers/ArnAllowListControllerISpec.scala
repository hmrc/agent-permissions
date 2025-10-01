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

import play.api.http.Status.OK
import play.api.libs.json.JsNull
import play.api.test.Helpers._
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.model.{Arn, BetaInviteRecord}
import uk.gov.hmrc.agentpermissions.repository.BetaInviteRepository

class ArnAllowListControllerISpec extends ComponentBaseISpec {

  val arnAllowedUrl: String = s"$baseUrl/arn-allowed"
  val privateBetaInviteUrl: String = s"$baseUrl/private-beta-invite"
  val privateBetaDeclineUrl: String = s"$baseUrl/private-beta-invite/decline"

  val repo = inject[BetaInviteRepository]

  s"GET $arnAllowedUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(arnAllowedUrl)

      result.status shouldBe OK
    }
  }

  s"GET $privateBetaInviteUrl" should {
    s"return $OK when ARN already present on the allow list" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(privateBetaInviteUrl)

      result.status shouldBe OK

    }

    s"return $OK when ARN found in the beta-invite Mongo collection" in {

      givenAuthorisedAsAgentWith("RARN8603525")
      await(repo.upsert(BetaInviteRecord(Arn("RARN8603525"), "id-1", hideBetaInvite = true)))

      val result = get(privateBetaInviteUrl)

      result.status shouldBe OK
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith("RARN8603525")

      val result = get(privateBetaInviteUrl)

      result.status shouldBe NOT_FOUND
    }
  }

  s"POST $privateBetaDeclineUrl" should {
    s"return $CREATED" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = post(privateBetaDeclineUrl)(JsNull)

      result.status shouldBe CREATED
    }

    s"return $CONFLICT" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.upsert(BetaInviteRecord(arn, "id-1")))

      val result = post(privateBetaDeclineUrl)(JsNull)

      result.status shouldBe CONFLICT
    }
  }
}
