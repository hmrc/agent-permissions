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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.model.{UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup}

object UserEnrolmentAssignmentOps {

  def split(userEnrolmentAssignments: UserEnrolmentAssignments, chunkSize: Int): Seq[UserEnrolmentAssignments] = {
    val assignsChunks = userEnrolmentAssignments.assign
      .grouped(chunkSize)
      .map(assignsChunk =>
        UserEnrolmentAssignments(
          assign = assignsChunk,
          unassign = Set.empty[UserEnrolment],
          arn = userEnrolmentAssignments.arn
        )
      )
      .toSeq

    val unassignsChunks = userEnrolmentAssignments.unassign
      .grouped(chunkSize)
      .map(unassignsChunk =>
        UserEnrolmentAssignments(
          assign = Set.empty[UserEnrolment],
          unassign = unassignsChunk,
          arn = userEnrolmentAssignments.arn
        )
      )
      .toSeq

    assignsChunks ++ unassignsChunks
  }

  def forGroupCreation(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] = {

    val seedAssigns = explodeUserEnrolments(accessGroupToProcess)

    val seedUnassigns = Set.empty[UserEnrolment]

    Option(
      optimiseUserEnrolmentAssignments(
        accessGroupToProcess.arn,
        accessGroupToProcess.groupName,
        existingAccessGroups,
        seedAssigns,
        seedUnassigns
      )
    )
  }

  // TODO: split into forAddToGroup and forRemoveFromGroup
  def forGroupUpdate(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] =
    existingAccessGroups.find(_.groupName.equalsIgnoreCase(accessGroupToProcess.groupName)) map {
      accessGroupToProcessPreviousVersion =>
        val seedAssigns =
          explodeUserEnrolments(accessGroupToProcess) -- explodeUserEnrolments(accessGroupToProcessPreviousVersion)

        val seedUnassigns =
          explodeUserEnrolments(accessGroupToProcessPreviousVersion) -- explodeUserEnrolments(accessGroupToProcess)

        optimiseUserEnrolmentAssignments(
          accessGroupToProcess.arn,
          accessGroupToProcess.groupName,
          existingAccessGroups,
          seedAssigns,
          seedUnassigns
        )
    }

  /** Could be merged with forRemoveFromGroup if you had a bool for isAssigns
    *
    * One set will always be the add, the other will be the existing set in the group being changed. For example:
    *   - agentUsers to add to group AND existing clients in the group
    *   - client to add from group AND existing agentUsers in that group
    * @param teamMembers
    *   add to group OR existing in group
    * @param clients
    *   add to group OR existing in group
    */
  def forAddToGroup(
    clients: Set[Client], // need enrolment key
    teamMembers: Set[AgentUser], // need ids
    existingAccessGroups: Seq[CustomGroup],
    arn: Arn,
    groupName: String
  ): Option[UserEnrolmentAssignments] = {
    val maxNetChange = pairUserEnrolments(teamMembers, clients)
    Option(optimiseUserEnrolmentAssignments(arn, groupName, existingAccessGroups, maxNetChange, Set.empty))
  }

  /** One set will always be the remove and the other will be the existing set in the group being changed. For example:
    *   - agentUsers to remove to group AND existing clients in the group
    *   - client to remove from group AND existing agentUsers in that group
    * @param teamMembers
    *   to remove from group OR existing in group
    * @param clients
    *   to remove from group OR existing in group
    */
  def forRemoveFromGroup(
    clients: Set[Client], // need enrolment key
    teamMembers: Set[AgentUser], // need ids
    existingAccessGroups: Seq[CustomGroup],
    arn: Arn,
    groupName: String
  ): Option[UserEnrolmentAssignments] = {
    val maxNetChange = pairUserEnrolments(teamMembers, clients)
    Option(optimiseUserEnrolmentAssignments(arn, groupName, existingAccessGroups, Set.empty, maxNetChange))
  }

  def forGroupDeletion(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] = {

    val seedAssigns = Set.empty[UserEnrolment]

    val seedUnassigns = explodeUserEnrolments(accessGroupToProcess)

    Option(
      optimiseUserEnrolmentAssignments(
        accessGroupToProcess.arn,
        accessGroupToProcess.groupName,
        existingAccessGroups,
        seedAssigns,
        seedUnassigns
      )
    )
  }

  private def explodeUserEnrolments(accessGroup: CustomGroup): Set[UserEnrolment] = for {
    userId       <- accessGroup.teamMembers.map(_.id);
    enrolmentKey <- accessGroup.clients.map(_.enrolmentKey)
  } yield UserEnrolment(userId, enrolmentKey)

  /** Returns maximum number of pairs for a NET change to EACD. Can be achieved without the full group since changes are
    * more restricted
    *
    * One set will always be the add/remove and the other will be the existing set in the group being changed. For
    * example:
    *   - agentUsers to add to group AND existing clients in the group
    *   - client to remove from group AND existing agentUsers in that group
    * @param agentUsers
    *   add or remove from group OR existing in group
    * @param clients
    *   add or remove from group OR existing in group
    */
  def pairUserEnrolments(agentUsers: Set[AgentUser], clients: Set[Client]): Set[UserEnrolment] = for {
    userId       <- agentUsers.map(_.id)
    enrolmentKey <- clients.map(_.enrolmentKey)
  } yield UserEnrolment(userId, enrolmentKey)

  private def optimiseUserEnrolmentAssignments(
    arn: Arn,
    groupName: String,
    existingAccessGroups: Seq[CustomGroup],
    seedAssigns: Set[UserEnrolment],
    seedUnassigns: Set[UserEnrolment]
  ): UserEnrolmentAssignments =
    existingAccessGroups
      .filterNot(_.groupName.equalsIgnoreCase(groupName))
      .foldLeft(UserEnrolmentAssignments(seedAssigns, seedUnassigns, arn)) {
        (userEnrolmentAssignments, existingAccessGroup) =>
          val userEnrolments = explodeUserEnrolments(existingAccessGroup)

          userEnrolmentAssignments.copy(
            assign = userEnrolmentAssignments.assign -- userEnrolments,
            unassign = userEnrolmentAssignments.unassign -- userEnrolments
          )
      }

  /** Returns optimal assignments for a NET change to EACD by taking the maximum pairs and checking to see if they
    * already exist via other access groups.
    *
    * If removing a client/team member but the assignment pair exists in another group, the UserEnrolment should not be
    * removed (remove from net change set). If adding a client/team member but the assignment pair exists in another
    * group, the UserEnrolment does not need to be added (remove from net change set).
    *
    * Once these UserEnrolments have been removed from the max net change assignments can be processed
    * @param maxNetChange
    *   the max enrolments to either assign or unassign in EACD
    * @param foundPairs
    *   the pairs found in existing access groups to remove from net change
    */
  def assessUserEnrolmentPairs(
    arn: Arn,
    foundPairs: Option[Set[UserEnrolment]],
    maxNetChange: Set[UserEnrolment],
    isNetChangeAssign: Boolean // add = true, remove = false
  ): UserEnrolmentAssignments =
    foundPairs
      .foldLeft(
        if (isNetChangeAssign) {
          UserEnrolmentAssignments(assign = maxNetChange, Set.empty, arn)
        } else {
          UserEnrolmentAssignments(Set.empty, unassign = maxNetChange, arn)
        }
      ) { (userEnrolmentAssignments, foundPairs) =>
        if (isNetChangeAssign) {
          userEnrolmentAssignments.copy(
            assign = userEnrolmentAssignments.assign -- foundPairs,
            unassign = userEnrolmentAssignments.unassign
          )
        } else {
          userEnrolmentAssignments.copy(
            assign = userEnrolmentAssignments.assign,
            unassign = userEnrolmentAssignments.unassign -- foundPairs
          )
        }
      }

}
