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
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, Client, CustomGroup}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime

class SensitiveAccessGroupSpec extends BaseSpec {

  val agentUser1 = AgentUser("agentUser1", "Robert Smith")
  val agentUser2 = AgentUser("agentUser2", "Sandy Jones")
  val client1 = Client("HMRC-MTD-VAT~VRN~123456789", "")
  val client2 = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "")

  val accessGroup = CustomGroup(
    arn = Arn("KARN1234567"),
    groupName = "My Group",
    created = LocalDateTime.now().minusDays(1),
    lastUpdated = LocalDateTime.now().minusHours(1),
    createdBy = agentUser1,
    lastUpdatedBy = agentUser2,
    teamMembers = Some(Set(agentUser1, agentUser2)),
    clients = Some(Set(client1, client2))
  )

  "SensitiveAccessGroup" should {
    "serialise and unserialise correctly" in {
      implicit val crypto: Encrypter with Decrypter = aesGcmCrypto
      val sensitiveAccessGroup = SensitiveAccessGroup(accessGroup)
      val json = Json.toJson(sensitiveAccessGroup)
      val deserialised: SensitiveAccessGroup = Json.fromJson[SensitiveAccessGroup](json).get
      deserialised shouldBe sensitiveAccessGroup
      deserialised.decryptedValue shouldBe accessGroup
    }

    "be serialised to JSON with certain fields encrypted" in {
      implicit val crypto: Encrypter with Decrypter = aesGcmCrypto
      val sensitiveAccessGroup = SensitiveAccessGroup(accessGroup)
      val json = Json.toJson(sensitiveAccessGroup)
      (json \ "createdBy" \ "id").as[String] should not be accessGroup.createdBy.id
      (json \ "createdBy" \ "name").as[String] should not be accessGroup.createdBy.name
      (json \ "lastUpdatedBy" \ "id").as[String] should not be accessGroup.lastUpdatedBy.id
      (json \ "lastUpdatedBy" \ "name").as[String] should not be accessGroup.lastUpdatedBy.name
      val memberIds = accessGroup.teamMembers.get.toSeq.indices.map { index =>
        (json \ "teamMembers" \ index \ "id").as[String]
      }
      val memberNames = accessGroup.teamMembers.get.toSeq.indices.map { index =>
        (json \ "teamMembers" \ index \ "name").as[String]
      }
      memberIds should contain noElementsOf accessGroup.teamMembers.get.map(_.id)
      memberNames should contain noElementsOf accessGroup.teamMembers.get.map(_.name)
    }
  }
}
