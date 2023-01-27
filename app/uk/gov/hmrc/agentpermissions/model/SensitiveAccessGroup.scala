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

import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, CustomGroup}
import uk.gov.hmrc.crypto._

/** Variant of CustomGroup suitable to be stored partially encrypted in Mongo. (APB-6461)
  */
case class SensitiveAccessGroup(override val decryptedValue: CustomGroup) extends Sensitive[CustomGroup]

object SensitiveAccessGroup {
  private def encryptAgentUser(plainTextAgentUser: AgentUser)(implicit crypto: Encrypter): AgentUser =
    plainTextAgentUser.copy(
      id = crypto.encrypt(PlainText(plainTextAgentUser.id)).value,
      name = crypto.encrypt(PlainText(plainTextAgentUser.name)).value
    )
  private def decryptAgentUser(cryptedAgentUser: AgentUser)(implicit crypto: Decrypter): AgentUser =
    cryptedAgentUser.copy(
      id = crypto.decrypt(Crypted(cryptedAgentUser.id)).value,
      name = crypto.decrypt(Crypted(cryptedAgentUser.name)).value
    )
  private def encryptFields(accessGroup: CustomGroup)(implicit crypto: Encrypter): CustomGroup = accessGroup.copy(
    createdBy = encryptAgentUser(accessGroup.createdBy),
    lastUpdatedBy = encryptAgentUser(accessGroup.lastUpdatedBy),
    teamMembers = accessGroup.teamMembers.map(_.map(encryptAgentUser))
  )
  private def decryptFields(securedAccessGroup: CustomGroup)(implicit crypto: Decrypter): CustomGroup =
    securedAccessGroup.copy(
      createdBy = decryptAgentUser(securedAccessGroup.createdBy),
      lastUpdatedBy = decryptAgentUser(securedAccessGroup.lastUpdatedBy),
      teamMembers = securedAccessGroup.teamMembers.map(_.map(decryptAgentUser))
    )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveAccessGroup] =
    new Format[SensitiveAccessGroup] {
      def reads(json: JsValue): JsResult[SensitiveAccessGroup] =
        Json.fromJson[CustomGroup](json).map(o => SensitiveAccessGroup(decryptFields(o)))
      def writes(o: SensitiveAccessGroup): JsValue =
        Json.toJson(encryptFields(o.decryptedValue))
    }
}
