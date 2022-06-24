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
import uk.gov.hmrc.agentpermissions.connectors.{EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, RecordInserted, RecordUpdated}
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsServiceImpl])
trait AccessGroupsService {
  def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus]

  def getAllGroups(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroup]]

  def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]]

  def rename(groupId: GroupId, renameGroupTo: String, agentUser: AgentUser)(implicit
    ec: ExecutionContext
  ): Future[AccessGroupRenamingStatus]

  def delete(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupDeletionStatus]

  def update(groupId: GroupId, accessGroup: AccessGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList]

  def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

  def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]
}

@Singleton
class AccessGroupsServiceImpl @Inject() (
  accessGroupsRepository: AccessGroupsRepository,
  userEnrolmentAssignmentService: UserEnrolmentAssignmentService,
  userClientDetailsConnector: UserClientDetailsConnector
) extends AccessGroupsService with Logging {

  override def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus] =
    accessGroupsRepository.get(accessGroup.arn, accessGroup.groupName) flatMap {
      case Some(_) =>
        Future.successful(AccessGroupExistsForCreation)
      case _ =>
        for {
          maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupCreation(accessGroup)
          maybeCreationId            <- accessGroupsRepository.insert(accessGroup)
        } yield maybeCreationId match {
          case None =>
            AccessGroupNotCreated
          case Some(creationId) =>
            maybeCalculatedAssignments.foreach(pushCalculatedAssignments)

            val groupId = GroupId(accessGroup.arn, accessGroup.groupName).encode
            logger.info(s"Created access group. DB id: '$creationId', gid: '$groupId'")
            AccessGroupCreated(groupId)
        }
    }

  override def getAllGroups(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroup]] =
    accessGroupsRepository.get(arn)

  override def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]] =
    accessGroupsRepository.get(groupId.arn, groupId.groupName)

  override def rename(groupId: GroupId, renameGroupTo: String, whoIsRenaming: AgentUser)(implicit
    ec: ExecutionContext
  ): Future[AccessGroupRenamingStatus] =
    accessGroupsRepository.get(groupId.arn, groupId.groupName) flatMap {
      case None =>
        Future.successful(AccessGroupNotExistsForRenaming)
      case Some(_) =>
        accessGroupsRepository.renameGroup(groupId.arn, groupId.groupName, renameGroupTo, whoIsRenaming) flatMap {
          case None =>
            Future.successful(AccessGroupNotRenamed)
          case Some(upsertType) =>
            upsertType match {
              case RecordInserted(_) =>
                logger.warn("Should not have inserted when the request was for an update")
                Future.successful(AccessGroupNotRenamed)
              case RecordUpdated =>
                logger.info(s"Renamed access group")
                Future.successful(AccessGroupRenamed)
            }
        }
    }

  override def delete(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupDeletionStatus] =
    for {
      maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupDeletion(groupId)
      maybeDeletedCount          <- accessGroupsRepository.delete(groupId.arn, groupId.groupName)
    } yield maybeDeletedCount match {
      case None =>
        AccessGroupNotDeleted
      case Some(deletedCount) =>
        if (deletedCount == 1L) {
          maybeCalculatedAssignments.foreach(pushCalculatedAssignments)

          AccessGroupDeleted
        } else {
          AccessGroupNotDeleted
        }
    }

  override def update(groupId: GroupId, accessGroup: AccessGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(accessGroup, whoIsUpdating)
      maybeCalculatedAssignments   <- userEnrolmentAssignmentService.calculateForGroupUpdate(groupId, accessGroup)
      maybeUpdatedCount <- accessGroupsRepository.update(groupId.arn, groupId.groupName, accessGroupWithWhoIsUpdating)
    } yield maybeUpdatedCount match {
      case None =>
        AccessGroupNotUpdated
      case Some(updatedCount) =>
        if (updatedCount == 1L) {
          maybeCalculatedAssignments.foreach(pushCalculatedAssignments)

          AccessGroupUpdated
        } else {
          AccessGroupNotUpdated
        }
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

  private def pushCalculatedAssignments(
    calculatedAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus] =
    userEnrolmentAssignmentService
      .applyAssignmentsInEacd(calculatedAssignments)
      .map { pushStatus =>
        logger.info(s"Push status: $pushStatus")
        pushStatus
      }

}

sealed trait AccessGroupCreationStatus
case class AccessGroupCreated(creationId: String) extends AccessGroupCreationStatus
case object AccessGroupExistsForCreation extends AccessGroupCreationStatus
case object AccessGroupNotCreated extends AccessGroupCreationStatus

sealed trait AccessGroupRenamingStatus
case object AccessGroupNotExistsForRenaming extends AccessGroupRenamingStatus
case object AccessGroupNotRenamed extends AccessGroupRenamingStatus
case object AccessGroupRenamed extends AccessGroupRenamingStatus

sealed trait AccessGroupDeletionStatus
case object AccessGroupDeleted extends AccessGroupDeletionStatus
case object AccessGroupNotDeleted extends AccessGroupDeletionStatus

sealed trait AccessGroupUpdateStatus
case object AccessGroupNotUpdated extends AccessGroupUpdateStatus
case object AccessGroupUpdated extends AccessGroupUpdateStatus
