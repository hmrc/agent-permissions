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

package uk.gov.hmrc.agentpermissions.service

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.userenrolment.{AccessGroupSynchronizer, UserEnrolmentAssignmentService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsServiceImpl])
trait AccessGroupsService {
  def getById(id: String): Future[Option[AccessGroup]]

  def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus]

  def getAllGroups(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroup]]

  def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]]

  def delete(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupDeletionStatus]

  def update(groupId: GroupId, accessGroup: AccessGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList]

  def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

  def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class AccessGroupsServiceImpl @Inject() (
  accessGroupsRepository: AccessGroupsRepository,
  userEnrolmentAssignmentService: UserEnrolmentAssignmentService,
  userClientDetailsConnector: UserClientDetailsConnector,
  accessGroupSynchronizer: AccessGroupSynchronizer
) extends AccessGroupsService with Logging {

  override def getById(id: String): Future[Option[AccessGroup]] = accessGroupsRepository.findById(id)

  override def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus] = {

    def pushAssignments(
      maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
    ): Future[EacdAssignmentsPushStatus] =
      userEnrolmentAssignmentService.pushCalculatedAssignments(
        maybeCalculatedAssignments
      ) map { pushStatus =>
        logger.info(s"Push status: $pushStatus")
        pushStatus
      }

    accessGroupsRepository.get(accessGroup.arn, accessGroup.groupName) flatMap {
      case Some(_) =>
        Future.successful(AccessGroupExistsForCreation)
      case _ =>
        for {
          maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupCreation(accessGroup)
          maybeCreationId            <- accessGroupsRepository.insert(accessGroup)
          accessGroupCreationStatus <- maybeCreationId match {
                                         case None =>
                                           Future.successful(AccessGroupNotCreated)
                                         case Some(creationId) =>
                                           pushAssignments(maybeCalculatedAssignments) map { pushStatus =>
                                             logger.info(s"Created access group. DB id: '$creationId")

                                             if (pushStatus == AssignmentsPushed) AccessGroupCreated(creationId)
                                             else AccessGroupCreatedWithoutAssignmentsPushed(creationId)
                                           }
                                       }
        } yield accessGroupCreationStatus
    }
  }

  override def getAllGroups(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroup]] =
    accessGroupsRepository.get(arn)

  override def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]] =
    accessGroupsRepository.get(groupId.arn, groupId.groupName)

  override def delete(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupDeletionStatus] = {

    def pushAssignments(
      maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
    ): Future[EacdAssignmentsPushStatus] =
      userEnrolmentAssignmentService.pushCalculatedAssignments(
        maybeCalculatedAssignments
      ) map { pushStatus =>
        logger.info(s"Push status: $pushStatus")
        pushStatus
      }

    for {
      maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupDeletion(groupId)
      maybeDeletedCount          <- accessGroupsRepository.delete(groupId.arn, groupId.groupName)
      accessGroupDeletionStatus <- maybeDeletedCount match {
                                     case None =>
                                       Future.successful(AccessGroupNotDeleted)
                                     case Some(deletedCount) =>
                                       if (deletedCount == 1L) {
                                         pushAssignments(maybeCalculatedAssignments) map {
                                           case AssignmentsPushed =>
                                             AccessGroupDeleted
                                           case AssignmentsNotPushed =>
                                             AccessGroupDeletedWithoutAssignmentsPushed
                                         }
                                       } else {
                                         Future.successful(AccessGroupNotDeleted)
                                       }
                                   }
    } yield accessGroupDeletionStatus
  }

  override def update(groupId: GroupId, accessGroup: AccessGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] = {

    def pushAssignments(
      maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
    ): Future[EacdAssignmentsPushStatus] =
      userEnrolmentAssignmentService.pushCalculatedAssignments(
        maybeCalculatedAssignments
      ) map { pushStatus =>
        logger.info(s"Push status: $pushStatus")
        pushStatus
      }

    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(accessGroup, whoIsUpdating)
      maybeCalculatedAssignments   <- userEnrolmentAssignmentService.calculateForGroupUpdate(groupId, accessGroup)
      maybeUpdatedCount <- accessGroupsRepository.update(groupId.arn, groupId.groupName, accessGroupWithWhoIsUpdating)
      accessGroupUpdateStatus <- maybeUpdatedCount match {
                                   case None =>
                                     Future.successful(AccessGroupNotUpdated)
                                   case Some(updatedCount) =>
                                     if (updatedCount == 1L) {
                                       pushAssignments(maybeCalculatedAssignments) map {
                                         case AssignmentsPushed =>
                                           AccessGroupUpdated
                                         case AssignmentsNotPushed =>
                                           AccessGroupUpdatedWithoutAssignmentsPushed
                                       }
                                     } else {
                                       Future.successful(AccessGroupNotUpdated)
                                     }
                                 }
    } yield accessGroupUpdateStatus
  }

  override def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList] =
    for {
      clients      <- userClientDetailsConnector.getClients(arn).map(_.toSet.flatten)
      accessGroups <- if (clients.nonEmpty) accessGroupsRepository.get(arn) else Future.successful(Seq.empty)
      assignedEnrolmentKeys = accessGroups.flatMap(_.clients).flatten.toSet.flatMap(EnrolmentKey.enrolmentKeys)
    } yield clients.foldLeft(ClientList(Set.empty, Set.empty)) { (clientList, client) =>
      if (assignedEnrolmentKeys.contains(client.enrolmentKey)) {
        clientList.copy(assigned = clientList.assigned + client)
      } else {
        clientList.copy(unassigned = clientList.unassigned + client)
      }
    }

  override def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.assigned)

  override def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.unassigned)

  private def mergeWhoIsUpdating(accessGroup: AccessGroup, whoIsUpdating: AgentUser): Future[AccessGroup] =
    Future.successful(accessGroup.copy(lastUpdated = LocalDateTime.now(), lastUpdatedBy = whoIsUpdating))

  override def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    for {
      maybeGroupDelegatedEnrolments <- userClientDetailsConnector.getClientsWithAssignedUsers(arn)
      updateStatuses <-
        maybeGroupDelegatedEnrolments.fold(Future.successful(Seq.empty[AccessGroupUpdateStatus]))(
          groupDelegatedEnrolments => accessGroupSynchronizer.syncWithEacd(arn, groupDelegatedEnrolments, whoIsUpdating)
        )
    } yield updateStatuses

}

sealed trait AccessGroupCreationStatus
case class AccessGroupCreated(creationId: String) extends AccessGroupCreationStatus
case object AccessGroupExistsForCreation extends AccessGroupCreationStatus
case object AccessGroupNotCreated extends AccessGroupCreationStatus
case class AccessGroupCreatedWithoutAssignmentsPushed(creationId: String) extends AccessGroupCreationStatus

sealed trait AccessGroupDeletionStatus
case object AccessGroupDeleted extends AccessGroupDeletionStatus
case object AccessGroupNotDeleted extends AccessGroupDeletionStatus
case object AccessGroupDeletedWithoutAssignmentsPushed extends AccessGroupDeletionStatus

sealed trait AccessGroupUpdateStatus
case object AccessGroupNotUpdated extends AccessGroupUpdateStatus
case object AccessGroupUpdated extends AccessGroupUpdateStatus
case object AccessGroupUpdatedWithoutAssignmentsPushed extends AccessGroupUpdateStatus
