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
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption.sensitiveEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveAgentUser(id: SensitiveString, name: SensitiveString, encrypted: Option[Boolean])
    extends Sensitive[AgentUser] {
  def decryptedValue: AgentUser = AgentUser(id.decryptedValue, name.decryptedValue)
}

object SensitiveAgentUser {
  def apply(agentUser: AgentUser): SensitiveAgentUser =
    SensitiveAgentUser(SensitiveString(agentUser.id), SensitiveString(agentUser.name), Some(true))

  implicit def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveAgentUser] = {

    def writes: Writes[SensitiveAgentUser] = model =>
      Json.obj(
        "id"        -> sensitiveEncrypter[String, SensitiveString].writes(model.id),
        "name"      -> sensitiveEncrypter[String, SensitiveString].writes(model.name),
        "encrypted" -> true
      )

    def reads: Reads[SensitiveAgentUser] = (json: JsValue) => {
      val encrypted = (json \ "encrypted").asOpt[Boolean]
      val identifier = decryptToSensitive("id", Some(true), json)
      val name = decryptToSensitive("name", encrypted, json)
      JsSuccess(SensitiveAgentUser(identifier, name, encrypted))
    }

    Format(reads, writes)
  }
}
