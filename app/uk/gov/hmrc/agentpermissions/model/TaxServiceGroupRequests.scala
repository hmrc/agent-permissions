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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, Client, TaxServiceAccessGroup}

import java.time.LocalDateTime

case class CreateTaxServiceGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  service: String,
  autoUpdate: Boolean = true,
  excludedClients: Option[Set[Client]] = None
) {
  def buildTaxServiceGroup(
    arn: Arn,
    agentUser: AgentUser
  ): TaxServiceAccessGroup = {
    val now = LocalDateTime.now()

    TaxServiceAccessGroup(
      arn,
      Option(groupName).map(_.trim).getOrElse(""),
      now,
      now,
      agentUser,
      agentUser,
      teamMembers,
      service,
      autoUpdate,
      excludedClients
    )
  }
}

object CreateTaxServiceGroupRequest {
  implicit val formatCreateTaxServiceGroupRequest: OFormat[CreateTaxServiceGroupRequest] =
    Json.format[CreateTaxServiceGroupRequest]
}

case class UpdateTaxServiceGroupRequest(
  groupName: Option[String],
  teamMembers: Option[Set[AgentUser]],
  autoUpdate: Option[Boolean] = None,
  excludedClients: Option[Set[Client]] = None
) {

  def merge(existingGroup: TaxServiceAccessGroup): TaxServiceAccessGroup = {
    val withMergedGroupName =
      groupName.fold(existingGroup)(name => existingGroup.copy(groupName = Option(name).map(_.trim).getOrElse("")))

    val withMergedTeamMembers =
      teamMembers.fold(withMergedGroupName)(tms => withMergedGroupName.copy(teamMembers = Some(tms)))

    val withExcludedClients = excludedClients.fold(withMergedTeamMembers)(exClients =>
      withMergedTeamMembers.copy(excludedClients = Some(exClients))
    )

    autoUpdate.fold(withExcludedClients)(au => withExcludedClients.copy(automaticUpdates = au))
  }
}

object UpdateTaxServiceGroupRequest {
  implicit val format: OFormat[UpdateTaxServiceGroupRequest] = Json.format[UpdateTaxServiceGroupRequest]
}

case class AddMembersToTaxServiceGroupRequest(teamMembers: Option[Set[AgentUser]], excludedClients: Option[Set[Client]])

object AddMembersToTaxServiceGroupRequest {
  implicit val format: OFormat[AddMembersToTaxServiceGroupRequest] = Json.format[AddMembersToTaxServiceGroupRequest]
}
