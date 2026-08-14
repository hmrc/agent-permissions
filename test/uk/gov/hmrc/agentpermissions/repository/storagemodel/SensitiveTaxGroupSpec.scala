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
import uk.gov.hmrc.agentpermissions.model.{Arn, SensitiveTaxGroup}
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, TaxGroup}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime

class SensitiveTaxGroupSpec extends TestConstants {

  given crypto: Encrypter & Decrypter = aesCrypto

  val agentUser: AgentUser = AgentUser(id = "agentUser1", name = "Robert Smith")
  val client: Client = Client(enrolmentKey = "HMRC-MTD-VAT~VRN~123456789", friendlyName = "Smith Roberts")

  override val taxGroup: TaxGroup = TaxGroup(
    id = GroupId.fromString("00000abc-6789-6789-6789-0000000000aa"),
    arn = Arn("KARN1234567"),
    groupName = "some group",
    created = LocalDateTime.of(2020, 1, 1, 0, 0, 0, 1000),
    lastUpdated = LocalDateTime.of(2020, 1, 1, 0, 0, 0, 1000),
    createdBy = agentUser,
    lastUpdatedBy = agentUser,
    teamMembers = Set(agentUser),
    service = "HMRC-MTD-VAT",
    excludedClients = Set(client),
    automaticUpdates = true
  )
  val sensitiveTaxGroup: SensitiveTaxGroup = SensitiveTaxGroup(taxGroup)

  "SensitiveTaxGroup" should {

    "read & write to JSON" in {
      val json = Json.toJson(sensitiveTaxGroup)
      json.as[SensitiveTaxGroup] shouldBe sensitiveTaxGroup
    }
  }
}
