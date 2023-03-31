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

package uk.gov.hmrc.agentpermissions.repository.storagemodel

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime

class SensitiveCustomGroupSpec extends BaseSpec {

  val agentUser1 = SensitiveAgentUser(SensitiveString("agentUser1"), SensitiveString("Robert Smith"))
  val agentUser2 = SensitiveAgentUser(SensitiveString("agentUser2"), SensitiveString("Sandy Jones"))
  val client1 = SensitiveClient(SensitiveString("HMRC-MTD-VAT~VRN~123456789"), SensitiveString(""))
  val client2 =
    SensitiveClient(SensitiveString("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"), SensitiveString(""))

  val sensitiveCustomGroup = SensitiveCustomGroup(
    _id = GroupId.random().toString,
    arn = Arn("KARN1234567"),
    groupName = "My Group",
    created = LocalDateTime.now().minusDays(1),
    lastUpdated = LocalDateTime.now().minusHours(1),
    createdBy = agentUser1,
    lastUpdatedBy = agentUser2,
    teamMembers = Set(agentUser1, agentUser2),
    clients = Set(client1, client2)
  )

  "SensitiveAccessGroup" should {
    "serialise and unserialise correctly" in {
      implicit val crypto: Encrypter with Decrypter = aesCrypto
      val json = Json.toJson(sensitiveCustomGroup)
      val deserialised: SensitiveCustomGroup = Json.fromJson[SensitiveCustomGroup](json).get
      deserialised shouldBe sensitiveCustomGroup
    }

    "be serialised to JSON with certain fields encrypted" in {
      implicit val crypto: Encrypter with Decrypter = aesCrypto
      val json = Json.toJson(sensitiveCustomGroup)
      (json \ "createdBy" \ "id").as[String] should not be sensitiveCustomGroup.createdBy.id
      (json \ "createdBy" \ "name").as[String] should not be sensitiveCustomGroup.createdBy.name
      (json \ "lastUpdatedBy" \ "id").as[String] should not be sensitiveCustomGroup.lastUpdatedBy.id
      (json \ "lastUpdatedBy" \ "name").as[String] should not be sensitiveCustomGroup.lastUpdatedBy.name
      val memberIds = sensitiveCustomGroup.teamMembers.toSeq.indices.map { index =>
        (json \ "teamMembers" \ index \ "id").as[String]
      }
      val memberNames = sensitiveCustomGroup.teamMembers.toSeq.indices.map { index =>
        (json \ "teamMembers" \ index \ "name").as[String]
      }
      memberIds should contain noElementsOf sensitiveCustomGroup.teamMembers.map(_.id)
      memberNames should contain noElementsOf sensitiveCustomGroup.teamMembers.map(_.name)
    }
  }
}
