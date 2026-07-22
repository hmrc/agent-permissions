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

import play.api.libs.json.JsNull
import play.api.test.Helpers.*
import support.ComponentBaseISpec
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.{OptinEvent, OptinRecord}
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.OptinEventType.*
import uk.gov.hmrc.agentpermissions.repository.OptinRepository

import java.time.LocalDateTime

class OptinControllerISpec extends ComponentBaseISpec {

  val optInUrl: String = s"$baseUrl/arn/${arn.value}/optin"
  val optOutUrl: String = s"$baseUrl/arn/${arn.value}/optout"
  val optInStatus: String = s"$baseUrl/arn/${arn.value}/optin-status"
  val optInRecordExistsUrl: String = s"$baseUrl/arn/${arn.value}/optin-record-exists"

  val repo: OptinRepository = inject[OptinRepository]

  s"POST $optInUrl" should {
    s"return $CREATED" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn, sendEmail = true)

      val result = post(optInUrl)(JsNull)

      result.status shouldBe CREATED
    }
    s"return $CONFLICT" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn, sendEmail = true)
      await(repo.upsert(OptinRecord(arn, List(OptinEvent(OptedIn, AgentUser("id1", "tm1"), LocalDateTime.now)))))

      val result = post(optInUrl)(JsNull)

      result.status shouldBe CONFLICT
    }

    s"return $BAD_REQUEST" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = post(s"$baseUrl/arn/DARN00000012/optin")(JsNull)

      println(result.body)

      result.status shouldBe BAD_REQUEST

    }
  }

  s"POST $optOutUrl" should {
    s"return $CREATED" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)

      val result = post(optOutUrl)(JsNull)

      result.status shouldBe CREATED

    }
    s"return $CONFLICT" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenGetClientsSuccess(arn)
      await(repo.upsert(OptinRecord(arn, List.empty)))

      val result = post(optOutUrl)(JsNull)

      result.status shouldBe CONFLICT
    }
  }

  s"GET $optInStatus" should {
    s"return $OK" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.upsert(OptinRecord(arn, List(OptinEvent(OptedIn, AgentUser("id1", "tm1"), LocalDateTime.now)))))
      givenUserCheck(arn)
      givenOutstandingWorkItems(arn)

      val result = get(optInStatus)

      result.status shouldBe OK
    }

    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)
      givenUserCheck(arn, 500)
      givenAgentSizeSuccess(arn)

      val result = get(optInStatus)

      result.status shouldBe NOT_FOUND
    }
  }

  s"GET $optInRecordExistsUrl" should {
    s"return $NO_CONTENT" in {

      givenAuthorisedAsAgentWith(arn.value)
      await(repo.upsert(OptinRecord(arn, List(OptinEvent(OptedIn, AgentUser("id1", "tm1"), LocalDateTime.now)))))

      val result = get(optInRecordExistsUrl)

      result.status shouldBe NO_CONTENT
    }
    s"return $NOT_FOUND" in {

      givenAuthorisedAsAgentWith(arn.value)

      val result = get(optInRecordExistsUrl)

      result.status shouldBe NOT_FOUND
    }
  }

}
