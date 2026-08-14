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

import play.api.libs.json.Json
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.model.SensitiveClient
import uk.gov.hmrc.agentpermissions.model.accessgroups.Client
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

class SensitiveClientSpec extends TestConstants {

  given crypto: Encrypter & Decrypter = aesCrypto

  val client: Client = Client("HMRC-MTD-VAT~VRN~123456789", "Smith Roberts")
  val sensitiveClient: SensitiveClient = SensitiveClient(client)

  "SensitiveClient" should {

    "read & write to JSON" in {
      val json = Json.toJson(sensitiveClient)
      json.as[SensitiveClient] shouldBe sensitiveClient
    }
  }
}
