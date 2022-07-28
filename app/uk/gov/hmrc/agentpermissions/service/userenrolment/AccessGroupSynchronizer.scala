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
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.{AccessGroupNotUpdated, AccessGroupUpdateStatus, AccessGroupUpdated}

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[AccessGroupSynchronizerImpl])
trait AccessGroupSynchronizer {
  def syncWithEacd(
    arn: Arn,
    groupDelegatedEnrolments: GroupDelegatedEnrolments,
    whoIsUpdating: AgentUser
  )(implicit ec: ExecutionContext): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class AccessGroupSynchronizerImpl @Inject() (accessGroupsRepository: AccessGroupsRepository)
    extends AccessGroupSynchronizer with Logging {

  override def syncWithEacd(
    arn: Arn,
    groupDelegatedEnrolments: GroupDelegatedEnrolments,
    whoIsUpdating: AgentUser
  )(implicit ec: ExecutionContext): Future[Seq[AccessGroupUpdateStatus]] =
    for {
      accessGroups <- accessGroupsRepository.get(arn)
      removalSet = calculateRemovalSet(accessGroups, groupDelegatedEnrolments)
      accessGroupsWithUpdates <-
        applyRemovalsOnAccessGroups(accessGroups, removalSet, whoIsUpdating)
      groupUpdateStatuses <- persistAccessGroups(accessGroupsWithUpdates)
    } yield groupUpdateStatuses

  def calculateRemovalSet(
    accessGroups: Seq[AccessGroup],
    groupDelegatedEnrolments: GroupDelegatedEnrolments
  ): Set[UserEnrolment] = {
    val globalGroupView: Set[UserEnrolment] = userEnrolmentsOf(accessGroups)
    val eacdAllocatedView: Set[UserEnrolment] = userEnrolmentsOf(groupDelegatedEnrolments)

    val removalSet = globalGroupView.diff(eacdAllocatedView)
    logger.info(s"Calculated removal set: $removalSet")
    removalSet
  }

  def applyRemovalsOnAccessGroups(
    existingAccessGroups: Seq[AccessGroup],
    removalSet: Set[UserEnrolment],
    whoIsUpdating: AgentUser
  ): Future[Seq[AccessGroup]] = {

    val removalEnrolments: Set[Enrolment] = buildEnrolmentsFromKeys(removalSet.map(_.enrolmentKey)).flatten
    val removalUserIds: Set[String] = removalSet.map(_.userId)

    Future successful existingAccessGroups
      .map(removeClientsFromGroup(_, removalEnrolments, whoIsUpdating))
      .map(removeTeamMembersFromGroup(_, removalUserIds, whoIsUpdating))
  }

  def persistAccessGroups(accessGroups: Seq[AccessGroup])(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    Future.sequence(accessGroups.map { accessGroup =>
      logger.info(s"Updating access group of ${accessGroup.arn} having name '${accessGroup.groupName}'")
      accessGroupsRepository
        .update(accessGroup.arn, accessGroup.groupName, accessGroup)
        .map {
          case None =>
            AccessGroupNotUpdated
          case Some(updatedCount) =>
            if (updatedCount == 1L) {
              AccessGroupUpdated
            } else {
              AccessGroupNotUpdated
            }
        }
    })

  private def userEnrolmentsOf(accessGroups: Seq[AccessGroup]): Set[UserEnrolment] =
    (for {
      accessGroup <- accessGroups
      enrolment   <- accessGroup.clients.toSeq.flatten
      identifier  <- enrolment.identifiers
      agentUser   <- accessGroup.teamMembers.toSeq.flatten
    } yield UserEnrolment(agentUser.id, EnrolmentKey.enrolmentKey(enrolment.service, identifier.value))).toSet

  private def userEnrolmentsOf(groupDelegatedEnrolments: GroupDelegatedEnrolments): Set[UserEnrolment] =
    (for {
      assignedClient <- groupDelegatedEnrolments.clients
      identifier     <- assignedClient.identifiers
    } yield UserEnrolment(
      assignedClient.assignedTo,
      EnrolmentKey.enrolmentKey(assignedClient.serviceName, identifier.value)
    )).toSet

  private def buildEnrolmentsFromKeys(enrolmentKeysToRemove: Set[String]): Set[Option[Enrolment]] =
    enrolmentKeysToRemove.map { enrolmentKeyToRemove =>
      Try {
        val parts = enrolmentKeyToRemove.split("~")
        Enrolment(parts(0), "", "", Seq(Identifier(parts(1), parts(2))))
      } match {
        case Success(enrolment) => Some(enrolment)
        case Failure(ex) =>
          logger.error(s"Unable to parse enrolment '$enrolmentKeyToRemove': ${ex.getMessage}")
          None
      }
    }

  private def removeClientsFromGroup(
    accessGroup: AccessGroup,
    removalEnrolments: Set[Enrolment],
    whoIsUpdating: AgentUser
  ): AccessGroup = {

    def findEnrolmentsToRemoveFromAccessGroup(enrolmentsOfAccessGroup: Set[Enrolment]): Set[Enrolment] =
      enrolmentsOfAccessGroup.foldLeft(Set.empty[Enrolment]) { (acc, enrolmentOfAccessGroup) =>
        removalEnrolments.find(removalEnrolment =>
          removalEnrolment.service == enrolmentOfAccessGroup.service && removalEnrolment.identifiers == enrolmentOfAccessGroup.identifiers
        ) match {
          case None        => acc
          case Some(found) => acc + found
        }
      }

    accessGroup.clients match {
      case None =>
        accessGroup
      case Some(enrolments) =>
        val enrolmentsToRemoveFromAccessGroup = findEnrolmentsToRemoveFromAccessGroup(enrolments)

        if (enrolmentsToRemoveFromAccessGroup.nonEmpty) {
          accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            clients = Some(
              enrolments.filterNot(enrolment =>
                enrolmentsToRemoveFromAccessGroup.exists(enrolmentToRemove =>
                  enrolmentToRemove.service == enrolment.service && enrolmentToRemove.identifiers == enrolment.identifiers
                )
              )
            )
          )
        } else {
          accessGroup
        }
    }
  }

  private def removeTeamMembersFromGroup(
    accessGroup: AccessGroup,
    removalUserIds: Set[String],
    whoIsUpdating: AgentUser
  ) = {

    def findUsersToRemoveFromAccessGroup(agentUsersOfAccessGroup: Set[AgentUser]) =
      agentUsersOfAccessGroup.foldLeft(Set.empty[AgentUser]) { (acc, agentUserOfAccessGroup) =>
        removalUserIds.find(_ == agentUserOfAccessGroup.id) match {
          case None    => acc
          case Some(_) => acc + agentUserOfAccessGroup
        }
      }

    accessGroup.teamMembers match {
      case None =>
        accessGroup
      case Some(agentUsers) =>
        val usersToRemoveFromAccessGroup = findUsersToRemoveFromAccessGroup(agentUsers)

        if (usersToRemoveFromAccessGroup.nonEmpty) {
          accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            teamMembers = Some(
              agentUsers.filterNot(agentUser =>
                usersToRemoveFromAccessGroup.exists(userToRemove => agentUser.id == userToRemove.id)
              )
            )
          )
        } else {
          accessGroup
        }
    }
  }

}
