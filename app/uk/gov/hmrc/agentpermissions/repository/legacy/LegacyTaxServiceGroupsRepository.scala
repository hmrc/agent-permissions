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

package uk.gov.hmrc.agentpermissions.repository.legacy

import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.LocalDateTime
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
@deprecated("This repository will be migrated away from")
class LegacyTaxServiceGroupsRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aesGcm") crypto: Encrypter with Decrypter
) extends PlayMongoRepository[LegacySensitiveTaxServiceGroup](
      collectionName = "tax-service-groups",
      /* Note: we have to specify manually the encryption algorithm of the legacy DB rather than rely on injection */
      domainFormat = LegacySensitiveTaxServiceGroup.format(crypto),
      mongoComponent = mongoComponent,
      indexes = Seq.empty
    ) with Logging

@deprecated
case class LegacyTaxGroup(
  _id: String,
  arn: Arn,
  groupName: String,
  created: LocalDateTime,
  lastUpdated: LocalDateTime,
  createdBy: AgentUser,
  lastUpdatedBy: AgentUser,
  teamMembers: Option[Set[AgentUser]],
  service: String,
  automaticUpdates: Boolean,
  excludedClients: Option[Set[Client]]
)

object LegacyTaxGroup {
  implicit val format: OFormat[LegacyTaxGroup] = Json.format[LegacyTaxGroup]
}

case class LegacySensitiveTaxServiceGroup(override val decryptedValue: LegacyTaxGroup) extends Sensitive[LegacyTaxGroup]

object LegacySensitiveTaxServiceGroup {
  private def decryptAgentUser(cryptedAgentUser: AgentUser)(implicit crypto: Decrypter): AgentUser =
    cryptedAgentUser.copy(
      id = crypto.decrypt(Crypted(cryptedAgentUser.id)).value,
      name = crypto.decrypt(Crypted(cryptedAgentUser.name)).value
    )
  private def decryptFields(securedAccessGroup: LegacyTaxGroup)(implicit crypto: Decrypter): LegacyTaxGroup =
    securedAccessGroup.copy(
      createdBy = decryptAgentUser(securedAccessGroup.createdBy),
      lastUpdatedBy = decryptAgentUser(securedAccessGroup.lastUpdatedBy),
      teamMembers = securedAccessGroup.teamMembers.map(_.map(decryptAgentUser))
    )

  implicit def format(implicit crypto: Decrypter): Format[LegacySensitiveTaxServiceGroup] =
    new Format[LegacySensitiveTaxServiceGroup] {
      def reads(json: JsValue): JsResult[LegacySensitiveTaxServiceGroup] =
        Json.fromJson[LegacyTaxGroup](json).map(o => LegacySensitiveTaxServiceGroup(decryptFields(o)))
      def writes(o: LegacySensitiveTaxServiceGroup): JsValue = throw new NotImplementedError()
    }
}
