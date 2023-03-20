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

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, EacdSyncRepository, TaxServiceGroupsRepository}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.util.GroupOps
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class RemovalSet(enrolmentKeysToRemove: Set[String], userIdsToRemove: Set[String]) {
  def isEmpty: Boolean = enrolmentKeysToRemove.isEmpty && userIdsToRemove.isEmpty
}

sealed trait SyncResult
object SyncResult {
  case object AccessGroupUpdateSuccess extends SyncResult
  case object AccessGroupUpdateFailure extends SyncResult
  case object AccessGroupUnchanged extends SyncResult
}

@ImplementedBy(classOf[EacdSynchronizerImpl])
trait EacdSynchronizer {
  def syncWithEacd(arn: Arn, fullSync: Boolean = false)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Map[SyncResult, Int]]]
}

@Singleton
class EacdSynchronizerImpl @Inject() (
  userClientDetailsConnector: UserClientDetailsConnector,
  accessGroupsRepository: AccessGroupsRepository,
  taxServiceGroupsRepository: TaxServiceGroupsRepository,
  eacdSyncRepository: EacdSyncRepository,
  auditService: AuditService,
  actorSystem: ActorSystem,
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
    if (excludedClientsRemoved.nonEmpty)
      auditService.auditAccessGroupExcludedClientsRemoval(accessGroup, excludedClientsRemoved)
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
  ): Future[Unit] = {
    logger.info(s"Starting full sync for $arn.")
    val allUserIds: Set[String] = customGroups.flatMap(_.teamMembers.getOrElse(Set.empty).map(_.id)).toSet
    Future
      .traverse(allUserIds) { userId =>
        val expectedAssignments = customGroups
          .filter(_.teamMembers.getOrElse(Set.empty).map(_.id).contains(userId))
          .flatMap(_.clients.getOrElse(Set.empty).map(_.enrolmentKey))
        userClientDetailsConnector.syncTeamMember(arn, userId, expectedAssignments).transformWith { res =>
          Future.successful((userId, res))
        }
      // return value is Future of (userId, Success(bool: updated or not)) or (userId, Failure(exception))
      }
      .map { fullSyncResults =>
        val resyncedCount = fullSyncResults.count { case (_, Success(isChanged)) => isChanged }
        val exceptions = fullSyncResults.collect { case (userId, Failure(e)) => (userId, e) }
        val failuresText = if (exceptions.isEmpty) "No failures." else s"${exceptions.size} failures."
        logger.info(
          s"Full sync finished for $arn. $resyncedCount users of ${fullSyncResults.size} needed syncing in EACD. $failuresText"
        )
        if (exceptions.nonEmpty) {
          val failuresDetails = exceptions.map { case (userId, e) => s"userId $userId got ${e.getMessage}" }
          logger.warn(s"Full sync for $arn failed for the following users: " + failuresDetails.mkString("; "))
        }
      }
  }

  private[service] def persistAccessGroup(accessGroup: AccessGroup)(implicit
    ec: ExecutionContext
  ): Future[SyncResult] = {
    logger.info(
      s"Updating access group of ${accessGroup.arn.value} having name '${accessGroup.groupName}'"
    )
    (accessGroup match {
      case customGroup: CustomGroup =>
        accessGroupsRepository.update(customGroup.arn, customGroup.groupName, customGroup)
      case taxGroup: TaxGroup =>
        taxServiceGroupsRepository.update(taxGroup.arn, taxGroup.groupName, taxGroup)
    }).map {
      case Some(updatedCount) if updatedCount == 1L => SyncResult.AccessGroupUpdateSuccess
      case _                                        => SyncResult.AccessGroupUpdateFailure
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
                             case None | Some(true) =>
                               logger.warn(
                                 "Could not ensure that outstanding assignment queue was empty. Not doing sync"
                               )
                               Future.successful(None)
                             case Some(false) => eacdSyncRepository.acquire(arn, appConfig.eacdSyncNotBeforeSeconds)
                           }

    result <- maybeEacdSyncRecord match {
                case None =>
                  logger.warn("Could not acquire sync record. Not doing sync")
                  Future.successful(None)
                case _ =>
                  logger.info(s"Acquired record for EACD Sync for '${arn.value}'")
                  action.map(Some(_))
              }
  } yield result

  /** Ensure that access groups stored in agent-permissions do not contain any clients or team members that no longer
    * appear to be with the agency (as reported by EACD) Optionally (if requesting a 'full sync') also force the
    * assigned enrolments in EACD to match those stored here.
    * @return
    *   None if sync was not done (if too soon after previous sync or items still outstanding). A list of update
    *   statuses otherwise
    */
  def syncWithEacd(arn: Arn, fullSync: Boolean = false)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Map[SyncResult, Int]]] = ifSyncShouldOccur(arn) {
    val whoIsUpdating = AgentUser(id = "", name = "EACD sync")
    for {
      customAccessGroups <- accessGroupsRepository.get(arn)
      taxServiceGroups   <- taxServiceGroupsRepository.get(arn)
      allAccessGroups: Seq[AccessGroup] = customAccessGroups ++ taxServiceGroups

      // Determine list of clients and team members that should be removed from all access groups.
      removalSet <- calculateRemovalSet(arn, allAccessGroups)
      _ =
        logger.info(
          s"Calculated removal set for $arn: clients=${removalSet.enrolmentKeysToRemove.size} users=${removalSet.userIdsToRemove.size}"
        )

      // Remove clients and members that are no longer with the agency from any stored access groups.
      updatedAccessGroups <- Future.traverse(allAccessGroups)(applyRemovalSet(_, removalSet, whoIsUpdating))

      // Determine which access groups have actually changed
      accessGroupsToPersist = allAccessGroups.zip(updatedAccessGroups).collect {
                                case (old, updated) if updated.lastUpdated != old.lastUpdated => updated
                              }
      nrUnchangedAccessGroups = updatedAccessGroups.size - accessGroupsToPersist.size

      // Persist the updated access groups.
      updateStatuses: Seq[SyncResult] <-
        if (removalSet.isEmpty) Future.successful(Seq.empty)
        else Future.traverse(accessGroupsToPersist)(persistAccessGroup(_))

      // Optionally ensure that in EACD the enrolment assignments match those kept by agent-permissions.
      // This is scheduled asynchronously as it could take some time.
      _ = if (fullSync) actorSystem.scheduler.scheduleOnce(FiniteDuration(0, "s")) {
            doFullSync(arn, updatedAccessGroups.collect { case cg: CustomGroup => cg })
          }
          else ()
    } yield Map[SyncResult, Int](
      SyncResult.AccessGroupUpdateSuccess -> updateStatuses.count(_ == SyncResult.AccessGroupUpdateSuccess),
      SyncResult.AccessGroupUpdateFailure -> updateStatuses.count(_ == SyncResult.AccessGroupUpdateFailure),
      SyncResult.AccessGroupUnchanged     -> nrUnchangedAccessGroups
    ).filter(_._2 > 0)
  }
}
