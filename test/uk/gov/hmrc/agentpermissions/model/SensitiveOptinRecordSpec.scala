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

package uk.gov.hmrc.agentpermissions.model

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime

class SensitiveOptinRecordSpec extends BaseSpec {

  val agentUser1 = AgentUser("agentUser1", "Robert Smith")
  val agentUser2 = AgentUser("agentUser2", "Sandy Jones")

  val optinRecord = OptinRecord(
    arn = Arn("KARN1234567"),
    history = List(
      OptinEvent(OptedIn, agentUser1, LocalDateTime.now().minusMonths(3)),
      OptinEvent(OptedOut, agentUser1, LocalDateTime.now().minusMonths(2)),
      OptinEvent(OptedIn, agentUser2, LocalDateTime.now().minusMonths(1))
    )
  )

  "SensitiveOptinRecord" should {
    "serialise and unserialise correctly" in {
      implicit val crypto: Encrypter with Decrypter = aesCrypto
      val sensitiveOptinRecord = SensitiveOptinRecord(optinRecord)
      val json = Json.toJson(sensitiveOptinRecord)
      val deserialised: SensitiveOptinRecord = Json.fromJson[SensitiveOptinRecord](json).get
      deserialised shouldBe sensitiveOptinRecord
      deserialised.decryptedValue shouldBe optinRecord
    }

    "be serialised to JSON with certain fields encrypted" in {
      implicit val crypto: Encrypter with Decrypter = aesCrypto
      val sensitiveOptinRecord = SensitiveOptinRecord(optinRecord)
      val json = Json.toJson(sensitiveOptinRecord)
      val userIds = optinRecord.history.indices.map { index =>
        (json \ "history" \ index \ "user" \ "id").as[String]
      }
      val userNames = optinRecord.history.indices.map { index =>
        (json \ "history" \ index \ "user" \ "name").as[String]
      }
      userIds should contain noElementsOf optinRecord.history.map(_.user.id)
      userNames should contain noElementsOf optinRecord.history.map(_.user.name)
    }
  }
}
