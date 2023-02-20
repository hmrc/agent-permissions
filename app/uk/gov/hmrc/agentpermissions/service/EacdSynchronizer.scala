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

package uk.gov.hmrc.agentpermissions.service

import akka.stream.Materializer
import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, CustomGroup, UserDetails}
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.util.GroupOps
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class RemovalSet(enrolmentKeysToRemove: Set[String], userIdsToRemove: Set[String]) {
  def isEmpty: Boolean = enrolmentKeysToRemove.isEmpty && userIdsToRemove.isEmpty
}

@ImplementedBy(classOf[EacdSynchronizerImpl])
trait EacdSynchronizer {
  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class EacdSynchronizerImpl @Inject() (
  userClientDetailsConnector: UserClientDetailsConnector,
  accessGroupsRepository: AccessGroupsRepository,
  auditService: AuditService
)(implicit materializer: Materializer)
    extends EacdSynchronizer with Logging {

  /** Interrogate EACD to find out whether the stored access groups are referencing any clients or team members who are
    * no longer with the agency. These clients and members should be removed from any groups they are in.
    * @return
    *   the lists of clients and team members which should removed from access groups.
    */
  private[service] def calculateRemovalSet(arn: Arn, accessGroups: Seq[CustomGroup])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[RemovalSet] = for {
    clientsInEacd <- userClientDetailsConnector
                       .getClients(arn)
                       .map(_.getOrElse(throw new RuntimeException("Could not retrieve client list from ES3")).toSet)
    membersInEacd: Set[UserDetails] <- userClientDetailsConnector.getTeamMembers(arn).map(_.toSet)
    clientsInAccessGroups = accessGroups.flatMap(_.clients.getOrElse(Seq.empty)).toSet
    membersInAccessGroups = accessGroups.flatMap(_.teamMembers.getOrElse(Set.empty)).toSet
    enrolmentKeysToRemove = clientsInAccessGroups.map(_.enrolmentKey).diff(clientsInEacd.map(_.enrolmentKey))
    userIdsToRemove = membersInAccessGroups.map(_.id).diff(membersInEacd.flatMap(_.userId))
  } yield RemovalSet(enrolmentKeysToRemove, userIdsToRemove)

  /** From each access group provided, remove the clients and members specified in the removal set. This function has
    * the side-effect of sending audit events to record the clients and members that are removed.
    * @return
    *   The updated list of access groups.
    */
  private[service] def applyRemovalSet(
    accessGroups: Seq[CustomGroup],
    removalSet: RemovalSet,
    whoIsUpdating: AgentUser
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Seq[CustomGroup]] = {
    // Step 1. Remove clients that are no longer with the agency from any stored access groups.
    val accessGroups1 = accessGroups.map { group =>
      val (updatedGroup, clientsRemovedFromGroup) =
        GroupOps.removeClientsFromGroup(
          group,
          removalSet.enrolmentKeysToRemove,
          whoIsUpdating
        )
      if (clientsRemovedFromGroup.nonEmpty) auditService.auditAccessGroupClientsRemoval(group, clientsRemovedFromGroup)
      updatedGroup
    }

    // Step 2. Remove members that are no longer with the agency from any stored access groups.
    val accessGroups2 = accessGroups1.map { group =>
      val (updatedGroup, membersRemovedFromGroup) =
        GroupOps.removeTeamMembersFromGroup(group, removalSet.userIdsToRemove, whoIsUpdating)
      if (membersRemovedFromGroup.nonEmpty)
        auditService.auditAccessGroupTeamMembersRemoval(group, membersRemovedFromGroup)
      updatedGroup
    }

    /* There is really no need for this function to return a Future, but since this function sends audit events
      a Future return type is a way to signal the presence of side effects */
    Future.successful(accessGroups2)
  }

  private[service] def persistAccessGroups(accessGroups: Seq[CustomGroup])(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] = Future.traverse(accessGroups) { accessGroup =>
    logger.info(
      s"Updating access group of ${accessGroup.arn.value} having name '${accessGroup.groupName}'"
    )
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
  }

  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    for {
      accessGroups <- accessGroupsRepository.get(arn)

      // Determine list of clients and team members that should be removed from all access groups.
      removalSet <- calculateRemovalSet(arn, accessGroups)
      _ = logger.info(s"Calculated removal set for $arn: $removalSet")

      // Remove members that are no longer with the agency from any stored access groups.
      updatedAccessGroups <- applyRemovalSet(accessGroups, removalSet, whoIsUpdating)

      // Persist the updated access groups.
      updateStatuses: Seq[AccessGroupUpdateStatus] <-
        if (removalSet.isEmpty) Future.successful(Seq.empty) else persistAccessGroups(updatedAccessGroups)

      // Next step: Ensure assignments in EACD reflect those in Agent Permissions
      // To be developed...

    } yield updateStatuses

}
