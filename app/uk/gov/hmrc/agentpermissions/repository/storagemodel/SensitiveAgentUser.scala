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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

case class SensitiveAgentUser(id: SensitiveString, name: SensitiveString) extends Sensitive[AgentUser] {
  def decryptedValue: AgentUser = AgentUser(id = id.decryptedValue, name = name.decryptedValue)
}

object SensitiveAgentUser {
  def apply(agentUser: AgentUser): SensitiveAgentUser =
    SensitiveAgentUser(id = SensitiveString(agentUser.id), name = SensitiveString(agentUser.name))
  implicit def formatAgentUser(implicit crypto: Encrypter with Decrypter): OFormat[SensitiveAgentUser] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveAgentUser]
  }
}
