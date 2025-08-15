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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, CustomGroup}

import java.time.LocalDateTime

case class CreateAccessGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Client]]
) {
  def buildAccessGroup(
    arn: Arn,
    agentUser: AgentUser
  ): CustomGroup = {
    val now = LocalDateTime.now()

    CustomGroup(
      GroupId.random(),
      arn,
      Option(groupName).map(_.trim).getOrElse(""),
      now,
      now,
      agentUser,
      agentUser,
      teamMembers.getOrElse(Set.empty),
      clients.getOrElse(Set.empty)
    )
  }
}

object CreateAccessGroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[CreateAccessGroupRequest] = Json.format[CreateAccessGroupRequest]
}

case class UpdateAccessGroupRequest(
  groupName: Option[String],
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Client]]
) {

  def merge(existingAccessGroup: CustomGroup): CustomGroup = {
    val withMergedGroupName = groupName.fold(existingAccessGroup)(name =>
      existingAccessGroup.copy(groupName = Option(name).map(_.trim).getOrElse(""))
    )
    val withMergedClients = clients.fold(withMergedGroupName)(cls => withMergedGroupName.copy(clients = cls))
    teamMembers.fold(withMergedClients)(members => withMergedClients.copy(teamMembers = members))
  }
}

object UpdateAccessGroupRequest {
  implicit val format: OFormat[UpdateAccessGroupRequest] = Json.format[UpdateAccessGroupRequest]
}

case class AddMembersToAccessGroupRequest(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]])

object AddMembersToAccessGroupRequest {
  implicit val format: OFormat[AddMembersToAccessGroupRequest] = Json.format[AddMembersToAccessGroupRequest]
}
