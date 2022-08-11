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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[GroupTeamMembersRemoverImpl])
trait GroupTeamMembersRemover {

  def removeTeamMembersFromGroup(
    accessGroup: AccessGroup,
    removalUserIds: Set[String],
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): AccessGroup
}

@Singleton
class GroupTeamMembersRemoverImpl @Inject() (auditConnector: AuditConnector)
    extends GroupTeamMembersRemover with Logging {

  override def removeTeamMembersFromGroup(
    accessGroup: AccessGroup,
    removalUserIds: Set[String],
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): AccessGroup =
    accessGroup.teamMembers match {
      case None =>
        accessGroup
      case Some(teamMembersOfAccessGroup) =>
        val teamMembersToRemoveFromAccessGroup =
          findUsersToRemoveFromAccessGroup(teamMembersOfAccessGroup, removalUserIds)

        if (teamMembersToRemoveFromAccessGroup.nonEmpty) {
          modifyAccessGroupWithTeamMembersRemoved(
            accessGroup,
            teamMembersOfAccessGroup,
            teamMembersToRemoveFromAccessGroup,
            whoIsUpdating
          )
        } else {
          accessGroup
        }
    }

  private def findUsersToRemoveFromAccessGroup(
    agentUsersOfAccessGroup: Set[AgentUser],
    removalUserIds: Set[String]
  ): Set[AgentUser] =
    agentUsersOfAccessGroup.foldLeft(Set.empty[AgentUser]) { (acc, agentUserOfAccessGroup) =>
      removalUserIds.find(_ == agentUserOfAccessGroup.id) match {
        case None    => acc
        case Some(_) => acc + agentUserOfAccessGroup
      }
    }

  private def modifyAccessGroupWithTeamMembersRemoved(
    accessGroup: AccessGroup,
    teamMembersOfAccessGroup: Set[AgentUser],
    teamMembersToRemoveFromAccessGroup: Set[AgentUser],
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): AccessGroup = {

    val (teamMembersToRemove, teamMembersToKeep): (Set[AgentUser], Set[AgentUser]) =
      teamMembersOfAccessGroup.partition(agentUser =>
        teamMembersToRemoveFromAccessGroup.exists(userToRemove => agentUser.id == userToRemove.id)
      )

    auditAccessGroupTeamMembersRemoval(accessGroup, teamMembersToRemove)

    accessGroup.copy(
      lastUpdated = LocalDateTime.now(),
      lastUpdatedBy = whoIsUpdating,
      teamMembers = Some(teamMembersToKeep)
    )
  }

  private def auditAccessGroupTeamMembersRemoval(accessGroup: AccessGroup, teamMembersToRemove: Set[AgentUser])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    auditConnector.sendExplicitAudit(
      "AccessGroupTeamMembersRemoval",
      Json.obj(
        "accessGroupId"      -> s"${accessGroup._id}",
        "accessGroupName"    -> s"${accessGroup.groupName}",
        "teamMembersRemoved" -> teamMembersToRemove
      )
    )

}
