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

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, CustomGroup, TaxGroup, UserDetails}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, EacdSyncRepository, TaxServiceGroupsRepository}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.util.GroupOps
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class RemovalSet(enrolmentKeysToRemove: Set[String], userIdsToRemove: Set[String]) {
  def isEmpty: Boolean = enrolmentKeysToRemove.isEmpty && userIdsToRemove.isEmpty
}

@ImplementedBy(classOf[EacdSynchronizerImpl])
trait EacdSynchronizer {
  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser, fullSync: Boolean = false)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class EacdSynchronizerImpl @Inject() (
  userClientDetailsConnector: UserClientDetailsConnector,
  accessGroupsRepository: AccessGroupsRepository,
  taxServiceGroupsRepository: TaxServiceGroupsRepository,
  eacdSyncRepository: EacdSyncRepository,
  auditService: AuditService,
  appConfig: AppConfig
) extends EacdSynchronizer with Logging {

  /** Interrogate EACD to find out whether the stored access groups are referencing any clients or team members who are
    * no longer with the agency. These clients and members should be removed from any groups they are in.
    *
    * @return
    *   the lists of clients and team members which should removed from access groups.
    */
  private[service] def calculateRemovalSet(arn: Arn, accessGroups: Seq[AccessGroup])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[RemovalSet] = if (accessGroups.isEmpty)
    Future.successful(RemovalSet(Set.empty, Set.empty))
  else
    for {
      clientsInEacd <- userClientDetailsConnector
                         .getClients(arn)
                         .map(_.getOrElse(throw new RuntimeException("Could not retrieve client list from ES3")).toSet)
      membersInEacd: Set[UserDetails] <- userClientDetailsConnector.getTeamMembers(arn).map(_.toSet)
      clientsInAccessGroups = accessGroups.flatMap {
                                case cg: CustomGroup => cg.clients.getOrElse(Set.empty)
                                case tg: TaxGroup    => tg.excludedClients.getOrElse(Set.empty)
                              }.toSet
      membersInAccessGroups = accessGroups.flatMap(_.teamMembers.getOrElse(Set.empty)).toSet
      enrolmentKeysToRemove = clientsInAccessGroups.map(_.enrolmentKey).diff(clientsInEacd.map(_.enrolmentKey))
      userIdsToRemove = membersInAccessGroups.map(_.id).diff(membersInEacd.flatMap(_.userId))
    } yield RemovalSet(enrolmentKeysToRemove, userIdsToRemove)

  /** From the access group provided, remove the clients and members specified in the removal set. This function has the
    * side-effect of sending audit events to record the clients and members that are removed.
    *
    * @return
    *   The updated list of access groups.
    */
  private[service] def applyRemovalSetToCustomGroup(
    accessGroup: CustomGroup,
    removalSet: RemovalSet,
    whoIsUpdating: AgentUser
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[CustomGroup] = {
    // Step 1. Remove clients in the removal set from the access group.
    val accessGroup1 = {
      val (updatedGroup, clientsRemovedFromGroup) =
        GroupOps.removeClientsFromGroup(
          accessGroup,
          removalSet.enrolmentKeysToRemove,
          whoIsUpdating
        )
      if (clientsRemovedFromGroup.nonEmpty)
        auditService.auditAccessGroupClientsRemoval(accessGroup, clientsRemovedFromGroup)
      updatedGroup
    }

    // Step 2. Remove members in the removal set from the access group.
    val accessGroup2 = {
      val (updatedGroup, membersRemovedFromGroup) =
        GroupOps.removeTeamMembersFromGroup(accessGroup1, removalSet.userIdsToRemove, whoIsUpdating)
      if (membersRemovedFromGroup.nonEmpty)
        auditService.auditAccessGroupTeamMembersRemoval(accessGroup1, membersRemovedFromGroup)
      updatedGroup
    }

    /* There is really no need for this function to return a Future, but since this function sends audit events
      a Future return type is a way to signal the presence of side effects */
    Future.successful(accessGroup2)
  }

  /** From the tax service access group provided, remove the clients and members specified in the removal set. This
    * function has the side-effect of sending audit events to record the clients and members that are removed.
    *
    * @return
    *   The updated tax service access group.
    */
  private[service] def applyRemovalSetToTaxGroup(
    accessGroup: TaxGroup,
    removalSet: RemovalSet,
    whoIsUpdating: AgentUser
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[TaxGroup] = {
    val updatedGroup = accessGroup.copy(
      teamMembers = accessGroup.teamMembers.map(_.filterNot(member => removalSet.userIdsToRemove.contains(member.id))),
      excludedClients = accessGroup.excludedClients.map(
        _.filterNot(client => removalSet.enrolmentKeysToRemove.contains(client.enrolmentKey))
      ),
      lastUpdated = LocalDateTime.now(),
      lastUpdatedBy = whoIsUpdating
    )
    val membersRemoved =
      accessGroup.teamMembers.getOrElse(Set.empty).diff(updatedGroup.teamMembers.getOrElse(Set.empty))
    val excludedClientsRemoved =
      accessGroup.excludedClients.getOrElse(Set.empty).diff(updatedGroup.excludedClients.getOrElse(Set.empty))

    if (membersRemoved.nonEmpty) auditService.auditAccessGroupTeamMembersRemoval(accessGroup, membersRemoved)
    // TODO should be sending an audit event when we remove an excluded client?
    /* There is really no need for this function to return a Future, but since this function sends audit events
      a Future return type is a way to signal the presence of side effects */
    val isChanged = membersRemoved.nonEmpty || excludedClientsRemoved.nonEmpty
    Future.successful(if (isChanged) updatedGroup else accessGroup)
  }

  private[service] def applyRemovalSet(
    accessGroup: AccessGroup,
    removalSet: RemovalSet,
    whoIsUpdating: AgentUser
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[AccessGroup] = accessGroup match {
    case customGroup: CustomGroup => applyRemovalSetToCustomGroup(customGroup, removalSet, whoIsUpdating)
    case taxGroup: TaxGroup       => applyRemovalSetToTaxGroup(taxGroup, removalSet, whoIsUpdating)
  }

  /** Force the assigned enrolments in EACD to match those stored here.
    */
  private[service] def doFullSync(arn: Arn, customGroups: Seq[CustomGroup])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Set[(String, Boolean)]] = {
    val allUserIds: Set[String] = customGroups.flatMap(_.teamMembers.getOrElse(Set.empty).map(_.id)).toSet
    Future.traverse(allUserIds) { userId =>
      val expectedAssignments = customGroups
        .filter(_.teamMembers.getOrElse(Set.empty).map(_.id).contains(userId))
        .flatMap(_.clients.getOrElse(Set.empty).map(_.enrolmentKey))
      userClientDetailsConnector.syncTeamMember(arn, userId, expectedAssignments).map((userId, _))
    }
  }

  private[service] def persistAccessGroup(accessGroup: AccessGroup)(implicit
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] = {
    logger.info(
      s"Updating access group of ${accessGroup.arn.value} having name '${accessGroup.groupName}'"
    )
    (accessGroup match {
      case customGroup: CustomGroup =>
        accessGroupsRepository.update(customGroup.arn, customGroup.groupName, customGroup)
      case taxGroup: TaxGroup =>
        taxServiceGroupsRepository.update(taxGroup.arn, taxGroup.groupName, taxGroup)
    }).map {
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

  /** Run the enclosed function only if safe (i.e. there are no outstanding assignment work item and the sync lock can
    * be acquired)
    */
  def ifSyncShouldOccur[A](arn: Arn)(action: => Future[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[A]] = for {
    maybeOutstandingAssignmentsWorkItemsExist <- userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn)

    maybeEacdSyncRecord <- maybeOutstandingAssignmentsWorkItemsExist match {
                             case None | Some(true) => Future.successful(None)
                             case Some(false) => eacdSyncRepository.acquire(arn, appConfig.eacdSyncNotBeforeSeconds)
                           }

    result <- maybeEacdSyncRecord match {
                case None => Future.successful(None)
                case _ =>
                  logger.info(s"Acquired record for EACD Sync for '${arn.value}'")
                  action.map(Some(_))
              }
  } yield result

  /** Ensure that access groups stored in agent-permissions do not contain any clients or team members that no longer
    * appear to be with the agency (as reported by EACD) Optionally (if requesting a 'full sync') also force the
    * assigned enrolments in EACD to match those stored here.
    */
  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser, fullSync: Boolean = false)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] = ifSyncShouldOccur(arn) {
    for {
      customAccessGroups <- accessGroupsRepository.get(arn)
      taxServiceGroups   <- taxServiceGroupsRepository.get(arn)
      allAccessGroups: Seq[AccessGroup] = customAccessGroups ++ taxServiceGroups

      // Determine list of clients and team members that should be removed from all access groups.
      removalSet <- calculateRemovalSet(arn, allAccessGroups)
      _ = logger.info(s"Calculated removal set for $arn: $removalSet")

      // Remove clients and members that are no longer with the agency from any stored access groups.
      updatedAccessGroups <- Future.traverse(allAccessGroups)(applyRemovalSet(_, removalSet, whoIsUpdating))

      // Persist the updated access groups.
      updateStatuses: Seq[AccessGroupUpdateStatus] <-
        if (removalSet.isEmpty) Future.successful(Seq.empty)
        else Future.traverse(updatedAccessGroups)(persistAccessGroup(_))

      // Optionally ensure that in EACD the enrolment assignments match those kept by agent-permissions.
      fullSyncResults <- if (!fullSync) Future.successful(Seq.empty)
                         else doFullSync(arn, updatedAccessGroups.collect { case cg: CustomGroup => cg })
      resyncedCount = fullSyncResults.count { case (_, isChanged) => isChanged }
      _ =
        if (fullSync)
          logger.info(s"Full sync performed. $resyncedCount users of ${fullSyncResults.size} needed syncing in EACD.")

    } yield updateStatuses
  }.map(_.getOrElse(Seq.empty))
}
