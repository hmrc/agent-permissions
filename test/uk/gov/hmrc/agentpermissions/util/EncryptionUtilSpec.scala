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

package uk.gov.hmrc.agentpermissions.util

import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption.sensitiveEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

class EncryptionUtilSpec extends BaseSpec {

  implicit val crypto: Encrypter with Decrypter = aesCrypto
  val unencrypted = "my secret"

  ".decryptToSensitive" should {

    "decrypt an encrypted string" in {
      val encrypted = sensitiveEncrypter[String, SensitiveString].writes(SensitiveString(unencrypted))
      encrypted shouldBe JsString("5uqG6DYthIX7naQg7kGEJQ==")
      val json = Json.obj("value" -> encrypted)

      EncryptionUtil.decryptToSensitive(
        "value",
        Some(true),
        json
      ) shouldBe SensitiveString(unencrypted)
    }

    "not attempt to decrypt a string that has an encryption flag of false" in {
      val json = Json.obj("value" -> unencrypted)
      EncryptionUtil.decryptToSensitive(
        "value",
        Some(false),
        json
      ) shouldBe SensitiveString(unencrypted)
    }

    "not attempt to decrypt a string that has no encryption flag" in {
      val json = Json.obj("value" -> unencrypted)
      EncryptionUtil.decryptToSensitive(
        "value",
        None,
        json
      ) shouldBe SensitiveString(unencrypted)
    }
  }
}
