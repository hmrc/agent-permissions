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

import play.api.libs.json._
import uk.gov.hmrc.agentpermissions.util.EncryptionUtil.decryptToSensitive
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption.sensitiveEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveClient(enrolmentKey: SensitiveString, friendlyName: SensitiveString, encrypted: Option[Boolean])
    extends Sensitive[Client] {
  def decryptedValue: Client = Client(enrolmentKey.decryptedValue, friendlyName.decryptedValue)
}

object SensitiveClient {
  def apply(client: Client): SensitiveClient = SensitiveClient(
    enrolmentKey = SensitiveString(client.enrolmentKey),
    friendlyName = SensitiveString(client.friendlyName),
    Some(true)
  )

  implicit def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveClient] = {

    def writes: Writes[SensitiveClient] = model =>
      Json.obj(
        "enrolmentKey" -> sensitiveEncrypter[String, SensitiveString].writes(model.enrolmentKey),
        "friendlyName" -> sensitiveEncrypter[String, SensitiveString].writes(model.friendlyName),
        "encrypted"    -> true
      )

    def reads: Reads[SensitiveClient] = (json: JsValue) => {
      val encrypted = (json \ "encrypted").asOpt[Boolean]
      val identifier = decryptToSensitive("enrolmentKey", Some(true), json)
      val name = decryptToSensitive("friendlyName", encrypted, json)
      JsSuccess(SensitiveClient(identifier, name, encrypted))
    }

    Format(reads, writes)
  }
}
