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

package uk.gov.hmrc.agentpermissions.repository.storagemodel

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

class SensitiveAgentUserSpec extends BaseSpec {

  implicit val crypto: Encrypter with Decrypter = aesCrypto

  val agentUser: AgentUser = AgentUser("agentUser1", "Robert Smith")
  val sensitiveAgentUser: SensitiveAgentUser = SensitiveAgentUser(agentUser)
  val sensitiveJson: JsObject = Json.obj(
    "id" -> "b1R0M181YgUTX4YUs596jg==",
    "name" -> "HXjWfzUOh3X5mPEI/Dbo2g==",
    "encrypted" -> true
  )

  "SensitiveAgentUser" should {

    "write to JSON" in {
      Json.toJson(sensitiveAgentUser) shouldBe sensitiveJson
    }

    "read from encrypted JSON" in {
      sensitiveJson.as[SensitiveAgentUser].decryptedValue shouldBe agentUser
    }

    "read from partially encrypted JSON" in {
      val partiallyEncryptedJson: JsObject = Json.obj(
        "id" -> "b1R0M181YgUTX4YUs596jg==",
        "name" -> "Robert Smith"
      )

      partiallyEncryptedJson.as[SensitiveAgentUser].decryptedValue shouldBe agentUser
    }
  }
}