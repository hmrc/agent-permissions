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

package uk.gov.hmrc.agentpermissions.util

import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client, CustomGroup}

import java.time.LocalDateTime

object GroupOps {

  /** Remove from a group all the clients who appear in the given list
    * @return
    *   the updated group and the set of clients actually removed.
    */
  def removeClientsFromGroup(
    accessGroup: CustomGroup,
    toRemove: Set[String],
    whoIsUpdating: AgentUser
  ): (CustomGroup, Set[Client]) =
    accessGroup.clients match {
      case None =>
        (accessGroup, Set.empty)
      case Some(clientsOfAccessGroup) =>
        val clientsToRemoveFromAccessGroup =
          // TODO: (LEGACY) Is the fold below the most efficient way of achieving this operation? Probably not
          clientsOfAccessGroup.foldLeft(Set.empty[Client]) { (acc, clientOfAccessGroup) =>
            toRemove.find(_ == clientOfAccessGroup.enrolmentKey) match {
              case None    => acc
              case Some(_) => acc + clientOfAccessGroup
            }
          }

        if (clientsToRemoveFromAccessGroup.nonEmpty) {
          val (clientsToRemove, clientsToKeep): (Set[Client], Set[Client]) =
            clientsOfAccessGroup.partition(client =>
              clientsToRemoveFromAccessGroup.exists(clientToRemove =>
                client.enrolmentKey == clientToRemove.enrolmentKey
              )
            )

          val modifiedAccessGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            clients = Some(clientsToKeep)
          )

          (modifiedAccessGroup, clientsToRemove)
        } else {
          (accessGroup, Set.empty)
        }
    }

  /** Remove from a group all the team members who appear in the given list
    * @return
    *   the updated group and the set of team members actually removed.
    */
  def removeTeamMembersFromGroup(
    accessGroup: CustomGroup,
    toRemove: Set[String],
    whoIsUpdating: AgentUser
  ): (CustomGroup, Set[AgentUser]) =
    accessGroup.teamMembers match {
      case None =>
        (accessGroup, Set.empty)
      case Some(teamMembersOfAccessGroup) =>
        val teamMembersToRemoveFromAccessGroup =
          // TODO: (LEGACY) Is the fold below the most efficient way of achieving this operation? Probably not
          teamMembersOfAccessGroup.foldLeft(Set.empty[AgentUser]) { (acc, agentUserOfAccessGroup) =>
            toRemove.find(_ == agentUserOfAccessGroup.id) match {
              case None    => acc
              case Some(_) => acc + agentUserOfAccessGroup
            }
          }

        if (teamMembersToRemoveFromAccessGroup.nonEmpty) {
          val (teamMembersToRemove, teamMembersToKeep): (Set[AgentUser], Set[AgentUser]) =
            teamMembersOfAccessGroup.partition(agentUser =>
              teamMembersToRemoveFromAccessGroup.exists(userToRemove => agentUser.id == userToRemove.id)
            )

          val modifiedAccessGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            teamMembers = Some(teamMembersToKeep)
          )
          (modifiedAccessGroup, teamMembersToRemove)
        } else {
          (accessGroup, Set.empty)
        }
    }

}
