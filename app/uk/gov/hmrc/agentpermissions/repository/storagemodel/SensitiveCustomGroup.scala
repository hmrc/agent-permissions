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
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.model.accessgroups.CustomGroup
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

import java.time.LocalDateTime

case class SensitiveCustomGroup(
  _id: String,
  arn: Arn,
  groupName: String,
  created: LocalDateTime,
  lastUpdated: LocalDateTime,
  createdBy: SensitiveAgentUser,
  lastUpdatedBy: SensitiveAgentUser,
  teamMembers: Set[SensitiveAgentUser],
  clients: Set[SensitiveClient],
  formatVersion: String = "2"
) extends Sensitive[CustomGroup] {
  def decryptedValue: CustomGroup = CustomGroup(
    id = GroupId.fromString(_id),
    arn = arn,
    groupName = groupName,
    created = created,
    lastUpdated = lastUpdated,
    createdBy = createdBy.decryptedValue,
    lastUpdatedBy = lastUpdatedBy.decryptedValue,
    teamMembers = teamMembers.map(_.decryptedValue),
    clients = clients.map(_.decryptedValue)
  )
}

object SensitiveCustomGroup {
  def apply(customGroup: CustomGroup): SensitiveCustomGroup =
    SensitiveCustomGroup(
      _id = customGroup.id.toString,
      arn = customGroup.arn,
      groupName = customGroup.groupName,
      created = customGroup.created,
      lastUpdated = customGroup.lastUpdated,
      createdBy = SensitiveAgentUser(customGroup.createdBy),
      lastUpdatedBy = SensitiveAgentUser(customGroup.lastUpdatedBy),
      teamMembers = customGroup.teamMembers.map(SensitiveAgentUser(_)),
      clients = customGroup.clients.map(SensitiveClient(_))
    )

  implicit def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveCustomGroup] =
    Json.format[SensitiveCustomGroup]
}
