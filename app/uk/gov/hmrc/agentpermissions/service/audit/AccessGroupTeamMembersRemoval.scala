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

package uk.gov.hmrc.agentpermissions.service.audit

import org.bson.types.ObjectId
import play.api.libs.json.{JsString, Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.AgentUser

case class AccessGroupTeamMembersRemoval(accessGroupId: ObjectId, accessGroupName: String, teamMembers: Set[AgentUser])

object AccessGroupTeamMembersRemoval {
  implicit val objectIdWrites: Writes[ObjectId] = Writes[ObjectId]((o: ObjectId) => JsString(o.toHexString))
  implicit val writes: Writes[AccessGroupTeamMembersRemoval] = Json.writes[AccessGroupTeamMembersRemoval]

  def split(
    accessGroupId: ObjectId,
    groupName: String,
    teamMembersToRemove: Set[AgentUser],
    chunkSize: Int
  ): Seq[AccessGroupTeamMembersRemoval] =
    teamMembersToRemove
      .grouped(chunkSize)
      .map { chunkedTeamMembers =>
        AccessGroupTeamMembersRemoval(accessGroupId, groupName, chunkedTeamMembers)
      }
      .toSeq
}
