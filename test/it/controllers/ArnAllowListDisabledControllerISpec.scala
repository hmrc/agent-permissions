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

package it.controllers

import play.api.http.Status.{NOT_FOUND, OK}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.model.BetaInviteRecord
import uk.gov.hmrc.agentpermissions.repository.BetaInviteRepository

class ArnAllowListDisabledControllerISpec extends ComponentBaseISpec {

  override def extraConfig(): Map[String, Any] =
    Map("features.check-arn-allow-list" -> false)

  private val repo = inject[BetaInviteRepository]

  val privateBetaInviteUrl: String = s"$baseUrl/private-beta-invite"

  s"GET $privateBetaInviteUrl" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.upsert(BetaInviteRecord(arn, "id-1", hideBetaInvite = true)))

      val result = get(privateBetaInviteUrl)

      result.status shouldBe OK
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(privateBetaInviteUrl)

      result.status shouldBe NOT_FOUND
    }
  }

}
