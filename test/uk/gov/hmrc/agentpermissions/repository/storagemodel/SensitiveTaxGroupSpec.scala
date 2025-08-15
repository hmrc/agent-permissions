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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, TaxGroup}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime
import java.util.UUID

class SensitiveTaxGroupSpec extends BaseSpec {

  implicit val crypto: Encrypter with Decrypter = aesCrypto

  val agentUser: AgentUser = AgentUser(id = "agentUser1", name = "Robert Smith")
  val client: Client = Client(enrolmentKey = "HMRC-MTD-VAT~VRN~123456789", friendlyName = "Smith Roberts")

  val taxGroup: TaxGroup = TaxGroup(
    id = UUID.fromString("00000abc-6789-6789-6789-0000000000aa"),
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

  val sensitiveJson: JsObject = Json.obj(
    "_id"         -> "00000abc-6789-6789-6789-0000000000aa",
    "arn"         -> "KARN1234567",
    "groupName"   -> "some group",
    "created"     -> "2020-01-01T00:00:00.000001",
    "lastUpdated" -> "2020-01-01T00:00:00.000001",
    "createdBy" -> Json.obj(
      "id"   -> "b1R0M181YgUTX4YUs596jg==",
      "name" -> "HXjWfzUOh3X5mPEI/Dbo2g=="
    ),
    "lastUpdatedBy" -> Json.obj(
      "id"   -> "b1R0M181YgUTX4YUs596jg==",
      "name" -> "HXjWfzUOh3X5mPEI/Dbo2g=="
    ),
    "teamMembers" -> Json.arr(
      Json.obj(
        "id"   -> "b1R0M181YgUTX4YUs596jg==",
        "name" -> "HXjWfzUOh3X5mPEI/Dbo2g=="
      )
    ),
    "service"          -> "HMRC-MTD-VAT",
    "automaticUpdates" -> true,
    "excludedClients" -> Json.arr(
      Json.obj(
        "enrolmentKey" -> "ddtpL0YcymEiA6dH+XLNcN2oYy6tDgEBCZrecQlriRE=",
        "friendlyName" -> "RRhGxwmDG4jML/ChHcNOYA=="
      )
    ),
    "formatVersion" -> "2"
  )

  "SensitiveTaxGroup" should {

    "write to JSON" in {
      Json.toJson(sensitiveTaxGroup) shouldBe sensitiveJson
    }

    "read from JSON" in {
      sensitiveJson.as[SensitiveTaxGroup].decryptedValue shouldBe taxGroup
    }
  }
}
