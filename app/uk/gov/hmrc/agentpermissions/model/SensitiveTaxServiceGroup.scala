/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, TaxServiceAccessGroup}
import uk.gov.hmrc.crypto._

/** Variant of TaxServiceAccessGroup suitable to be stored partially encrypted in Mongo.
  */
case class SensitiveTaxServiceGroup(override val decryptedValue: TaxServiceAccessGroup)
    extends Sensitive[TaxServiceAccessGroup]

object SensitiveTaxServiceGroup {
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
  private def encryptFields(accessGroup: TaxServiceAccessGroup)(implicit crypto: Encrypter): TaxServiceAccessGroup =
    accessGroup.copy(
      createdBy = encryptAgentUser(accessGroup.createdBy),
      lastUpdatedBy = encryptAgentUser(accessGroup.lastUpdatedBy),
      teamMembers = accessGroup.teamMembers.map(_.map(encryptAgentUser))
    )
  private def decryptFields(securedAccessGroup: TaxServiceAccessGroup)(implicit
    crypto: Decrypter
  ): TaxServiceAccessGroup =
    securedAccessGroup.copy(
      createdBy = decryptAgentUser(securedAccessGroup.createdBy),
      lastUpdatedBy = decryptAgentUser(securedAccessGroup.lastUpdatedBy),
      teamMembers = securedAccessGroup.teamMembers.map(_.map(decryptAgentUser))
    )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveTaxServiceGroup] =
    new Format[SensitiveTaxServiceGroup] {
      def reads(json: JsValue): JsResult[SensitiveTaxServiceGroup] =
        Json.fromJson[TaxServiceAccessGroup](json).map(o => SensitiveTaxServiceGroup(decryptFields(o)))
      def writes(o: SensitiveTaxServiceGroup): JsValue =
        Json.toJson(encryptFields(o.decryptedValue))
    }
}
