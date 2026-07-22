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
import uk.gov.hmrc.agentpermissions.connectors.AgentUserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.model.EacdAssignmentsPushStatus.{AssignmentsNotPushed, AssignmentsPushed}
import uk.gov.hmrc.agentpermissions.model.{Arn, DisplayClient, EacdAssignmentsPushStatus, EnrolmentKey, PaginatedList, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentService
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CustomGroupsServiceImpl])
trait CustomGroupsService {
  def addMemberToGroup(gid: GroupId, teamMember: AgentUser, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def getById(id: GroupId)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]]

  def getGroupByIdWithPageOfClientsToAdd(
    id: GroupId,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(GroupSummary, PaginatedList[DisplayClient])]]

  def create(
    accessGroup: CustomGroup
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus]

  def getAllCustomGroups(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]]

  def get(arn: Arn, groupName: String)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]]

  def getCustomGroupSummariesForClient(arn: Arn, enrolmentKey: String)(using
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

  def getCustomGroupSummariesForTeamMember(arn: Arn, userId: String)(using
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]
  def delete(arn: Arn, groupName: String, agentUser: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupDeletionStatus]

  def update(arn: Arn, groupName: String, accessGroup: CustomGroup, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def removeClient(groupId: GroupId, clientId: String, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def removeTeamMember(groupId: GroupId, teamMemberId: String, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def getAllClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList]

  def getAssignedClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

  def getUnassignedClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

}

case class ClientList(assigned: Set[Client], unassigned: Set[Client])

@Singleton
class CustomGroupsServiceImpl @Inject() (
  customGroupsRepository: CustomGroupsRepositoryV2,
  userEnrolmentAssignmentService: UserEnrolmentAssignmentService,
  taxGroupsService: TaxGroupsService,
  agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
  auditService: AuditService
) extends CustomGroupsService with Logging {

  override def getById(id: GroupId)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]] =
    customGroupsRepository
      .findById(id)
      .flatMap(withClientName)

  /** Gets a page of clients for the Arn and then finds if any of them are already in the group */
  override def getGroupByIdWithPageOfClientsToAdd(
    id: GroupId,
    page: Int,
    pageSize: Int,
    search: Option[String],
    filter: Option[String]
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(GroupSummary, PaginatedList[DisplayClient])]] =
    customGroupsRepository
      .findById(id)
      .flatMap {
        case None => Future.successful(None)
        case Some(grp) =>
          val enrolmentKeys = grp.clients.map(_.enrolmentKey)
          agentUserClientDetailsConnector
            .getPaginatedClients(grp.arn)(page, pageSize, search, filter)
            .map { paginatedClients =>
              val paginatedList = PaginatedList[DisplayClient](
                paginationMetaData = paginatedClients.paginationMetaData,
                // TODO: NEED TO ONLY LOAD THE CLIENTS FROM THE GROUP THAT ARE IN THIS PAGINATED LIST
                pageContent = paginatedClients.pageContent.map { c =>
                  DisplayClient.fromClient(c, enrolmentKeys.contains(c.enrolmentKey))
                }
              )
              Some((GroupSummary(grp.id, grp.groupName, None, grp.teamMembers.size, None), paginatedList))
            }
      }

  override def create(
    accessGroup: CustomGroup
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus] =
    customGroupsRepository.get(accessGroup.arn, accessGroup.groupName) flatMap {
      case Some(_) =>
        Future.successful(AccessGroupCreationStatus.AccessGroupExistsForCreation)
      case _ =>
        for {
          maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupCreation(accessGroup)
          maybeCreationId            <- customGroupsRepository.insert(withClientNamesRemoved(accessGroup))
          accessGroupCreationStatus <- maybeCreationId match {
                                         case None =>
                                           Future.successful(AccessGroupCreationStatus.AccessGroupNotCreated)
                                         case Some(creationId) =>
                                           for {
                                             pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                             _ <- Future.successful(auditService.auditAccessGroupCreation(accessGroup))
                                           } yield {
                                             logger.info(s"Created access group. DB id: '$creationId")

                                             if pushStatus == AssignmentsPushed then
                                               AccessGroupCreationStatus.AccessGroupCreated(creationId)
                                             else
                                               AccessGroupCreationStatus.AccessGroupCreatedWithoutAssignmentsPushed(
                                                 creationId
                                               )
                                           }
                                       }
        } yield accessGroupCreationStatus
    }

  override def getAllCustomGroups(
    arn: Arn
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]] =
    customGroupsRepository
      .get(arn)
      .flatMap(withClientNames)

  override def get(arn: Arn, groupName: String)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CustomGroup]] =
    customGroupsRepository
      .get(arn, groupName)
      .flatMap(withClientName)

  override def getCustomGroupSummariesForClient(arn: Arn, enrolmentKey: String)(using
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    customGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.clients.exists(_.enrolmentKey == enrolmentKey))
          .map(GroupSummary.of)
      )

  override def getCustomGroupSummariesForTeamMember(arn: Arn, userId: String)(using
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    customGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.teamMembers.exists(_.id == userId))
          .map(GroupSummary.of)
      )

  override def delete(arn: Arn, groupName: String, agentUser: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupDeletionStatus] =
    for {
      maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupDeletion(arn, groupName)
      maybeDeletedCount          <- customGroupsRepository.delete(arn, groupName)
      accessGroupDeletionStatus <- maybeDeletedCount match {
                                     case None =>
                                       Future.successful(AccessGroupDeletionStatus.AccessGroupNotDeleted)
                                     case Some(deletedCount) =>
                                       if deletedCount == 1L then
                                         for {
                                           pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                           _ <- Future.successful(
                                                  auditService.auditAccessGroupDeletion(arn, groupName, agentUser)
                                                )
                                         } yield pushStatus match {
                                           case AssignmentsPushed =>
                                             AccessGroupDeletionStatus.AccessGroupDeleted
                                           case AssignmentsNotPushed =>
                                             AccessGroupDeletionStatus.AccessGroupDeletedWithoutAssignmentsPushed
                                         }
                                       else Future.successful(AccessGroupDeletionStatus.AccessGroupNotDeleted)
                                   }
    } yield accessGroupDeletionStatus

  override def update(arn: Arn, groupName: String, accessGroup: CustomGroup, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(accessGroup, whoIsUpdating)
      maybeCalculatedAssignments <- userEnrolmentAssignmentService.calculateForGroupUpdate(arn, groupName, accessGroup)
      accessGroupUpdateStatus <- handleUpdate(
                                   arn,
                                   groupName,
                                   accessGroupWithWhoIsUpdating,
                                   maybeCalculatedAssignments
                                 )
    } yield accessGroupUpdateStatus

  /** Updates repo via replace (updated group must have lastUpdatedBy field replaced at same time) Then attempts to push
    * calculated assignments to EACD and audits outcome
    *
    * @param updatedGroup
    *   must include updated lastUpdatedBy and lastUpdated fields
    * @return
    *   AccessGroupUpdateStatus
    */
  private def handleUpdate(
    arn: Arn,
    groupName: String,
    updatedGroup: CustomGroup,
    maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupUpdateStatus] =
    for {
      maybeUpdatedCount <-
        customGroupsRepository
          .update(
            arn,
            groupName,
            withClientNamesRemoved(updatedGroup)
          ) // withClientNamesRemoved unnecessary unless adding clients
      accessGroupUpdateStatus <- maybeUpdatedCount match {
                                   case None =>
                                     logger.info(s"Access group '${updatedGroup.groupName}' not updated")
                                     Future.successful(AccessGroupUpdateStatus.AccessGroupNotUpdated)
                                   case Some(updatedCount) =>
                                     if updatedCount == 1L then
                                       for {
                                         pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                         _ <-
                                           Future.successful(
                                             auditService.auditAccessGroupUpdate(updatedGroup)
                                           ) // TODO update audit? instead of whole group, log NET change (userToRemove)
                                       } yield pushStatus match {
                                         case AssignmentsPushed =>
                                           AccessGroupUpdateStatus.AccessGroupUpdated
                                         case AssignmentsNotPushed =>
                                           AccessGroupUpdateStatus.AccessGroupUpdatedWithoutAssignmentsPushed
                                       }
                                     else
                                       logger.warn(
                                         s"Access group '${updatedGroup.groupName}' update count should not have been $updatedCount"
                                       )
                                       Future.successful(AccessGroupUpdateStatus.AccessGroupNotUpdated)
                                 }
    } yield accessGroupUpdateStatus

  def removeClient(groupId: GroupId, clientId: String, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    customGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeClients = accessGroup.clients.filterNot(tm => tm.enrolmentKey == clientId)
          val updatedGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            clients = maybeClients
          )

          val clientToRemove = accessGroup.clients.filter(tm => tm.enrolmentKey == clientId)
          val usersInGroup = accessGroup.teamMembers

          for {
            maybeCalculatedAssignments <-
              userEnrolmentAssignmentService
                .calculateForRemoveFromGroup(
                  accessGroup.arn,
                  accessGroup.groupName,
                  clientToRemove,
                  usersInGroup
                )
            updateStatus <-
              handleUpdate(accessGroup.arn, accessGroup.groupName, updatedGroup, maybeCalculatedAssignments)
          } yield updateStatus
        case _ => Future.successful(AccessGroupUpdateStatus.AccessGroupNotUpdated)
      }

  def removeTeamMember(groupId: GroupId, teamMemberId: String, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    customGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeAgentUsers = accessGroup.teamMembers.filterNot(tm => tm.id == teamMemberId)
          val userToRemove = accessGroup.teamMembers.filter(tm => tm.id == teamMemberId)
          val clientsInGroup = accessGroup.clients

          val updatedGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            teamMembers = maybeAgentUsers
          )
          for {
            maybeCalculatedAssignments <-
              userEnrolmentAssignmentService
                .calculateForRemoveFromGroup(
                  accessGroup.arn,
                  accessGroup.groupName,
                  clientsInGroup,
                  userToRemove
                )
            updateStatus <-
              handleUpdate(accessGroup.arn, accessGroup.groupName, updatedGroup, maybeCalculatedAssignments)
          } yield updateStatus
        case _ => Future.successful(AccessGroupUpdateStatus.AccessGroupNotUpdated)
      }

  // TODO move below to groups summary service
  override def getAllClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList] =
    for {
      clients      <- agentUserClientDetailsConnector.getClients(arn).map(_.toSet.flatten)
      accessGroups <- if clients.nonEmpty then customGroupsRepository.get(arn) else Future.successful(Seq.empty)
      enrolmentKeysInCustomGroups = accessGroups.toSet[CustomGroup].flatMap(_.clients).map(_.enrolmentKey)
      taxServiceGroups <- taxGroupsService.getAllTaxServiceGroups(arn)
    } yield clients.foldLeft(ClientList(Set.empty, Set.empty)) { (clientList, client) =>
      val serviceKey = EnrolmentKey.serviceOf(client.enrolmentKey) match {
        // both types of trusts and cbc client are represented by a single truncated key in tax service groups
        case "HMRC-TERS-ORG" | "HMRC-TERSNT-ORG"   => "HMRC-TERS"
        case "HMRC-CBC-ORG" | "HMRC-CBC-NONUK-ORG" => "HMRC-CBC"
        case sk                                    => sk
      }
      // The client is considered 'assigned' if: ...
      if enrolmentKeysInCustomGroups.contains(client.enrolmentKey) || // ... they are in a custom access group, OR ...
        taxServiceGroups.exists(tsg => // ... there is a tax service group AND they are not excluded from it.
          tsg.service == serviceKey &&
            !tsg.excludedClients.exists(_.enrolmentKey == client.enrolmentKey)
        )
      then clientList.copy(assigned = clientList.assigned + client)
      else clientList.copy(unassigned = clientList.unassigned + client)
    }

  override def getAssignedClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.assigned)

  override def getUnassignedClients(arn: Arn)(using hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.unassigned)
  // TODO move above to groups summary service

  private def mergeWhoIsUpdating(accessGroup: CustomGroup, whoIsUpdating: AgentUser): Future[CustomGroup] =
    Future.successful(accessGroup.copy(lastUpdated = LocalDateTime.now(), lastUpdatedBy = whoIsUpdating))

  private def pushAssignments(maybeCalculatedAssignments: Option[UserEnrolmentAssignments])(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[EacdAssignmentsPushStatus] =
    for {
      pushStatus <- userEnrolmentAssignmentService.pushCalculatedAssignments(maybeCalculatedAssignments)
    } yield {
      if pushStatus == AssignmentsPushed then
        maybeCalculatedAssignments.foreach(auditService.auditEsAssignmentUnassignments)
      else logger.info(s"Nothing to audit ES Assignment Unassignments as pushStatus: $pushStatus")

      logger.info(s"Push status: $pushStatus")
      pushStatus
    }

  private def withClientNamesRemoved(accessGroup: CustomGroup): CustomGroup =
    accessGroup.copy(clients = accessGroup.clients.map(_.copy(friendlyName = "")))

  private def withClientName(
    maybeAccessGroup: Option[CustomGroup]
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]] =
    maybeAccessGroup.fold(Future.successful(None))(accessGroup =>
      agentUserClientDetailsConnector
        .getClients(accessGroup.arn)
        .map(
          _.map(backendClients =>
            accessGroup.copy(clients = copyNamesFromBackendClients(accessGroup.clients)(backendClients))
          )
        )
    )

  private def copyNamesFromBackendClients(
    accessGroupClients: Set[Client]
  )(backendClients: Seq[Client]): Set[Client] = {

    def identifyFriendlyNameAmongBackendClients(accessGroupClient: Client): String =
      backendClients
        .find(_.enrolmentKey == accessGroupClient.enrolmentKey)
        .map(_.friendlyName)
        .getOrElse("")

    accessGroupClients.map(accessGroupClient =>
      accessGroupClient.copy(friendlyName = identifyFriendlyNameAmongBackendClients(accessGroupClient))
    )
  }

  private def withClientNames(
    accessGroups: Seq[CustomGroup]
  )(using hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]] =
    accessGroups match {
      case Nil => Future.successful(accessGroups)
      case accessGroups =>
        agentUserClientDetailsConnector.getClients(accessGroups.head.arn).map {
          case None => accessGroups
          case Some(backendClients) =>
            accessGroups.map(accessGroup =>
              accessGroup.copy(clients = copyNamesFromBackendClients(accessGroup.clients)(backendClients))
            )
        }
    }

  override def addMemberToGroup(groupId: GroupId, teamMemberToAdd: AgentUser, whoIsUpdating: AgentUser)(using
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    customGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val agentUsers = accessGroup.teamMembers ++ Set(teamMemberToAdd)
          val clientsInGroup = accessGroup.clients

          val updatedGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            teamMembers = agentUsers
          )
          for {
            maybeCalculatedAssignments <-
              userEnrolmentAssignmentService
                .calculateForAddToGroup(
                  accessGroup.arn,
                  accessGroup.groupName,
                  clientsInGroup,
                  Set(teamMemberToAdd)
                )
            updateStatus <-
              handleUpdate(accessGroup.arn, accessGroup.groupName, updatedGroup, maybeCalculatedAssignments)
          } yield updateStatus
        case _ => Future.successful(AccessGroupUpdateStatus.AccessGroupNotUpdated)
      }
}

enum AccessGroupCreationStatus {
  case AccessGroupCreated(creationId: String)
  case AccessGroupExistsForCreation
  case AccessGroupNotCreated
  case AccessGroupCreatedWithoutAssignmentsPushed(creationId: String)
}

enum AccessGroupDeletionStatus {
  case AccessGroupDeleted
  case AccessGroupNotDeleted
  case AccessGroupDeletedWithoutAssignmentsPushed
}

enum AccessGroupUpdateStatus {
  case AccessGroupNotUpdated
  case AccessGroupUpdated
  case AccessGroupUpdatedWithoutAssignmentsPushed
}
