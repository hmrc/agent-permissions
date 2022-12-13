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
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.service.userenrolment.{AccessGroupSynchronizer, UserEnrolmentAssignmentService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsServiceImpl])
trait AccessGroupsService {
  def getById(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]]

  def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus]

  def getAllGroups(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AccessGroup]]

  def get(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]]

  def getGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]]

  def getGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]]

  def delete(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupDeletionStatus]

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
  accessGroupSynchronizer: AccessGroupSynchronizer,
  auditService: AuditService
) extends AccessGroupsService with Logging {

  override def getById(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]] =
    accessGroupsRepository
      .findById(id)
      .flatMap(withClientName)

  override def create(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus] =
    accessGroupsRepository.get(accessGroup.arn, accessGroup.groupName) flatMap {
      case Some(_) =>
        Future.successful(AccessGroupExistsForCreation)
      case _ =>
        for {
          maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupCreation(accessGroup)
          maybeCreationId            <- accessGroupsRepository.insert(withClientNamesRemoved(accessGroup))
          accessGroupCreationStatus <- maybeCreationId match {
                                         case None =>
                                           Future.successful(AccessGroupNotCreated)
                                         case Some(creationId) =>
                                           for {
                                             pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                             _ <- Future successful auditService.auditAccessGroupCreation(accessGroup)
                                           } yield {
                                             logger.info(s"Created access group. DB id: '$creationId")

                                             if (pushStatus == AssignmentsPushed) AccessGroupCreated(creationId)
                                             else AccessGroupCreatedWithoutAssignmentsPushed(creationId)
                                           }
                                       }
        } yield accessGroupCreationStatus
    }

  override def getAllGroups(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AccessGroup]] =
    accessGroupsRepository
      .get(arn)
      .flatMap(withClientNames)

  override def get(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]] =
    accessGroupsRepository
      .get(groupId.arn, groupId.groupName)
      .flatMap(withClientName)

  override def getGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]] =
    accessGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.clients.fold(false)(_.map(_.enrolmentKey).contains(enrolmentKey)))
          .map(AccessGroupSummary.convertCustomGroup)
      )

  override def getGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]] =
    accessGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.teamMembers.fold(false)(_.map(_.id).contains(userId)))
          .map(AccessGroupSummary.convertCustomGroup)
      )

  override def delete(
    groupId: GroupId,
    agentUser: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupDeletionStatus] =
    for {
      maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupDeletion(groupId)
      maybeDeletedCount          <- accessGroupsRepository.delete(groupId.arn, groupId.groupName)
      accessGroupDeletionStatus <- maybeDeletedCount match {
                                     case None =>
                                       Future.successful(AccessGroupNotDeleted)
                                     case Some(deletedCount) =>
                                       if (deletedCount == 1L) {
                                         for {
                                           pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                           _ <-
                                             Future successful auditService
                                               .auditAccessGroupDeletion(groupId.arn, groupId.groupName, agentUser)
                                         } yield pushStatus match {
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

  override def update(groupId: GroupId, accessGroup: AccessGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(accessGroup, whoIsUpdating)
      maybeCalculatedAssignments   <- userEnrolmentAssignmentService.calculateForGroupUpdate(groupId, accessGroup)
      maybeUpdatedCount <-
        accessGroupsRepository
          .update(groupId.arn, groupId.groupName, withClientNamesRemoved(accessGroupWithWhoIsUpdating))
      accessGroupUpdateStatus <- maybeUpdatedCount match {
                                   case None =>
                                     logger.info(s"Access group '${accessGroup.groupName}' not updated")
                                     Future.successful(AccessGroupNotUpdated)
                                   case Some(updatedCount) =>
                                     if (updatedCount == 1L) {
                                       for {
                                         pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                         _ <- Future successful auditService.auditAccessGroupUpdate(
                                                accessGroupWithWhoIsUpdating
                                              )
                                       } yield pushStatus match {
                                         case AssignmentsPushed =>
                                           AccessGroupUpdated
                                         case AssignmentsNotPushed =>
                                           AccessGroupUpdatedWithoutAssignmentsPushed
                                       }
                                     } else {
                                       logger.warn(
                                         s"Access group '${accessGroup.groupName}' update count should not have been $updatedCount"
                                       )
                                       Future.successful(AccessGroupNotUpdated)
                                     }
                                 }
    } yield accessGroupUpdateStatus

  override def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList] =
    for {
      clients      <- userClientDetailsConnector.getClients(arn).map(_.toSet.flatten)
      accessGroups <- if (clients.nonEmpty) accessGroupsRepository.get(arn) else Future.successful(Seq.empty)
      assignedEnrolmentKeys = accessGroups.flatMap(_.clients).toSet.flatten.map(_.enrolmentKey)
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
      maybeOutstandingAssignmentsWorkItemsExist <- userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn)
      maybeGroupDelegatedEnrolments <- maybeOutstandingAssignmentsWorkItemsExist match {
                                         case None => Future successful None
                                         case Some(outstandingAssignmentsWorkItemsExist) =>
                                           if (outstandingAssignmentsWorkItemsExist) Future.successful(None)
                                           else userClientDetailsConnector.getClientsWithAssignedUsers(arn)
                                       }
      updateStatuses <-
        maybeGroupDelegatedEnrolments match {
          case None =>
            Future successful Seq.empty[AccessGroupUpdateStatus]
          case Some(groupDelegatedEnrolments) =>
            accessGroupSynchronizer.syncWithEacd(arn, groupDelegatedEnrolments, whoIsUpdating)
        }
    } yield updateStatuses

  private def pushAssignments(maybeCalculatedAssignments: Option[UserEnrolmentAssignments])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[EacdAssignmentsPushStatus] =
    for {
      pushStatus <- userEnrolmentAssignmentService.pushCalculatedAssignments(maybeCalculatedAssignments)
    } yield {
      if (pushStatus == AssignmentsPushed) {
        maybeCalculatedAssignments.foreach(auditService.auditEsAssignmentUnassignments)
      } else {
        logger.info(s"Nothing to audit ES Assignment Unassignments as pushStatus: $pushStatus")
      }

      logger.info(s"Push status: $pushStatus")
      pushStatus
    }

  private def withClientNamesRemoved(accessGroup: AccessGroup): AccessGroup =
    accessGroup.copy(clients = accessGroup.clients.map(_.map(_.copy(friendlyName = ""))))

  private def withClientName(
    maybeAccessGroup: Option[AccessGroup]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]] =
    maybeAccessGroup.fold(Future successful Option.empty[AccessGroup])(accessGroup =>
      userClientDetailsConnector
        .getClients(accessGroup.arn)
        .map(
          _.map(backendClients =>
            accessGroup.copy(clients = copyNamesFromBackendClients(accessGroup.clients)(backendClients))
          )
        )
    )

  private def copyNamesFromBackendClients(
    maybeAccessGroupClients: Option[Set[Client]]
  )(backendClients: Seq[Client]): Option[Set[Client]] = {

    def identifyFriendlyNameAmongBackendClients(accessGroupClient: Client): String =
      backendClients.find(_.enrolmentKey == accessGroupClient.enrolmentKey) match {
        case Some(matchingBackendClient) => matchingBackendClient.friendlyName
        case None                        => ""
      }

    maybeAccessGroupClients.map(
      _.map(accessGroupClient =>
        accessGroupClient.copy(friendlyName = identifyFriendlyNameAmongBackendClients(accessGroupClient))
      )
    )
  }

  private def withClientNames(
    accessGroups: Seq[AccessGroup]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AccessGroup]] =
    accessGroups match {
      case Nil => Future successful accessGroups
      case accessGroups =>
        userClientDetailsConnector.getClients(accessGroups.head.arn).map {
          case None => accessGroups
          case Some(backendClients) =>
            accessGroups.map(accessGroup =>
              accessGroup.copy(clients = copyNamesFromBackendClients(accessGroup.clients)(backendClients))
            )
        }
    }
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
