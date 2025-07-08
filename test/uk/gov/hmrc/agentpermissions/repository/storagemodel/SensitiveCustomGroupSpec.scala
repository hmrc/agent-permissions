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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime
import java.util.UUID

class SensitiveCustomGroupSpec extends BaseSpec {

  implicit val crypto: Encrypter with Decrypter = aesCrypto

  val agentUser: AgentUser = AgentUser("agentUser1", "Robert Smith")
  val client: Client = Client("HMRC-MTD-VAT~VRN~123456789", "Smith Roberts")

  val customGroup: CustomGroup = CustomGroup(
    UUID.fromString("00000abc-6789-6789-6789-0000000000aa"),
    Arn("KARN1234567"),
    "some group",
    LocalDateTime.of(2020, 1, 1, 0, 0, 0, 1000),
    LocalDateTime.of(2020, 1, 1, 0, 0, 0, 1000),
    agentUser,
    agentUser,
    Set(agentUser),
    Set(client)
  )
  val sensitiveCustomGroup: SensitiveCustomGroup = SensitiveCustomGroup(customGroup)

  val sensitiveJson: JsObject = Json.obj(
    "_id"         -> "00000abc-6789-6789-6789-0000000000aa",
    "arn"         -> "KARN1234567",
    "groupName"   -> "some group",
    "created"     -> "2020-01-01T00:00:00.000001",
    "lastUpdated" -> "2020-01-01T00:00:00.000001",
    "createdBy" -> Json.obj(
      "id"        -> "b1R0M181YgUTX4YUs596jg==",
      "name"      -> "HXjWfzUOh3X5mPEI/Dbo2g==",
      "encrypted" -> true
    ),
    "lastUpdatedBy" -> Json.obj(
      "id"        -> "b1R0M181YgUTX4YUs596jg==",
      "name"      -> "HXjWfzUOh3X5mPEI/Dbo2g==",
      "encrypted" -> true
    ),
    "teamMembers" -> Json.arr(
      Json.obj(
        "id"        -> "b1R0M181YgUTX4YUs596jg==",
        "name"      -> "HXjWfzUOh3X5mPEI/Dbo2g==",
        "encrypted" -> true
      )
    ),
    "clients" -> Json.arr(
      Json.obj(
        "enrolmentKey" -> "ddtpL0YcymEiA6dH+XLNcN2oYy6tDgEBCZrecQlriRE=",
        "friendlyName" -> "RRhGxwmDG4jML/ChHcNOYA==",
        "encrypted"    -> true
      )
    ),
    "formatVersion" -> "2",
    "encrypted"     -> true
  )

  "SensitiveCustomGroup" should {

    "write to JSON" in {
      Json.toJson(sensitiveCustomGroup) shouldBe sensitiveJson
    }

    "read from JSON" in {
      sensitiveJson.as[SensitiveCustomGroup].decryptedValue shouldBe customGroup
    }

    "read from partially encrypted JSON" in {
      val partiallyEncryptedJson: JsObject = Json.obj(
        "_id"         -> "00000abc-6789-6789-6789-0000000000aa",
        "arn"         -> "KARN1234567",
        "groupName"   -> "some group",
        "created"     -> "2020-01-01T00:00:00.000001",
        "lastUpdated" -> "2020-01-01T00:00:00.000001",
        "createdBy" -> Json.obj(
          "id"   -> "b1R0M181YgUTX4YUs596jg==",
          "name" -> "Robert Smith"
        ),
        "lastUpdatedBy" -> Json.obj(
          "id"   -> "b1R0M181YgUTX4YUs596jg==",
          "name" -> "Robert Smith"
        ),
        "teamMembers" -> Json.arr(
          Json.obj(
            "id"   -> "b1R0M181YgUTX4YUs596jg==",
            "name" -> "Robert Smith"
          )
        ),
        "clients" -> Json.arr(
          Json.obj(
            "enrolmentKey" -> "ddtpL0YcymEiA6dH+XLNcN2oYy6tDgEBCZrecQlriRE=",
            "friendlyName" -> "Smith Roberts"
          )
        ),
        "formatVersion" -> "2"
      )

      partiallyEncryptedJson.as[SensitiveCustomGroup].decryptedValue shouldBe customGroup
    }
  }
}
