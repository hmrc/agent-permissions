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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agents.accessgroups.TaxGroup
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

import java.time.LocalDateTime

case class SensitiveTaxGroup(
  _id: String,
  arn: Arn,
  groupName: String,
  created: LocalDateTime,
  lastUpdated: LocalDateTime,
  createdBy: SensitiveAgentUser,
  lastUpdatedBy: SensitiveAgentUser,
  teamMembers: Set[SensitiveAgentUser],
  service: String, // Nice to use Service but want flexibility for Trusts
  automaticUpdates: Boolean,
  excludedClients: Set[SensitiveClient],
  formatVersion: String = "2",
  encrypted: Option[Boolean]
) extends Sensitive[TaxGroup] {
  def decryptedValue: TaxGroup = TaxGroup(
    id = GroupId.fromString(_id),
    arn = arn,
    groupName = groupName,
    created = created,
    lastUpdated = lastUpdated,
    createdBy = createdBy.decryptedValue,
    lastUpdatedBy = lastUpdatedBy.decryptedValue,
    teamMembers = teamMembers.map(_.decryptedValue),
    service = service,
    automaticUpdates = automaticUpdates,
    excludedClients = excludedClients.map(_.decryptedValue)
  )
}

object SensitiveTaxGroup {
  implicit def apply(taxGroup: TaxGroup): SensitiveTaxGroup = SensitiveTaxGroup(
    _id = taxGroup.id.toString,
    arn = taxGroup.arn,
    groupName = taxGroup.groupName,
    created = taxGroup.created,
    lastUpdated = taxGroup.lastUpdated,
    createdBy = SensitiveAgentUser(taxGroup.createdBy),
    lastUpdatedBy = SensitiveAgentUser(taxGroup.lastUpdatedBy),
    teamMembers = taxGroup.teamMembers.map(SensitiveAgentUser(_)),
    service = taxGroup.service,
    automaticUpdates = taxGroup.automaticUpdates,
    excludedClients = taxGroup.excludedClients.map(SensitiveClient(_)),
    encrypted = Some(true)
  )

  implicit def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveTaxGroup] = {

    def writes: Writes[SensitiveTaxGroup] = model =>
      Json.obj(
        "_id"              -> model._id,
        "arn"              -> model.arn,
        "groupName"        -> model.groupName,
        "created"          -> model.created,
        "lastUpdated"      -> model.lastUpdated,
        "createdBy"        -> model.createdBy,
        "lastUpdatedBy"    -> model.lastUpdatedBy,
        "teamMembers"      -> model.teamMembers,
        "service"          -> model.service,
        "automaticUpdates" -> model.automaticUpdates,
        "excludedClients"  -> model.excludedClients,
        "formatVersion"    -> model.formatVersion,
        "encrypted"        -> true
      )

    def reads: Reads[SensitiveTaxGroup] = (json: JsValue) =>
      JsSuccess(
        SensitiveTaxGroup(
          (json \ "_id").as[String],
          (json \ "arn").as[Arn],
          (json \ "groupName").as[String],
          (json \ "created").as[LocalDateTime],
          (json \ "lastUpdated").as[LocalDateTime],
          (json \ "createdBy").as[SensitiveAgentUser],
          (json \ "lastUpdatedBy").as[SensitiveAgentUser],
          (json \ "teamMembers").as[Set[SensitiveAgentUser]],
          (json \ "service").as[String],
          (json \ "automaticUpdates").as[Boolean],
          (json \ "excludedClients").as[Set[SensitiveClient]],
          (json \ "formatVersion").as[String],
          (json \ "encrypted").asOpt[Boolean]
        )
      )

    Format(reads, writes)
  }
}
