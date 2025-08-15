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

package uk.gov.hmrc.agentpermissions.service.audit

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser

case class AccessGroupTeamMembersRemoval(
  agentReferenceNumber: String,
  accessGroupId: GroupId,
  accessGroupName: String,
  teamMembers: Set[AgentUser]
)

object AccessGroupTeamMembersRemoval {
  implicit val writes: Writes[AccessGroupTeamMembersRemoval] = Json.writes[AccessGroupTeamMembersRemoval]

  def split(
    agentReferenceNumber: String,
    accessGroupId: GroupId,
    groupName: String,
    teamMembersToRemove: Set[AgentUser],
    chunkSize: Int
  ): Seq[AccessGroupTeamMembersRemoval] =
    teamMembersToRemove
      .grouped(chunkSize)
      .map { chunkedTeamMembers =>
        AccessGroupTeamMembersRemoval(agentReferenceNumber, accessGroupId, groupName, chunkedTeamMembers)
      }
      .toSeq
}
