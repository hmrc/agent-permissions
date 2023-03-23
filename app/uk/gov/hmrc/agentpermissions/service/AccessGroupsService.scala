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
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.model.DisplayClient
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsServiceImpl])
trait AccessGroupsService {
  def addMemberToGroup(gid: String, teamMember: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def getById(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]]

  def getGroupByIdWithPageOfClientsToAdd(
    id: String,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(GroupSummary, PaginatedList[DisplayClient])]]

  def create(
    accessGroup: CustomGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AccessGroupCreationStatus]

  def getAllCustomGroups(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]]

  def get(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]]

  def getCustomGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

  def getCustomGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]
  def delete(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupDeletionStatus]

  def update(groupId: GroupId, accessGroup: CustomGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def removeClient(groupId: String, clientId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

  def removeTeamMember(groupId: String, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
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
  taxGroupsService: TaxGroupsService,
  userClientDetailsConnector: UserClientDetailsConnector,
  auditService: AuditService
) extends AccessGroupsService with Logging {

  override def getById(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]] =
    accessGroupsRepository
      .findById(id)
      .flatMap(withClientName)

  /** Gets a page of clients for the Arn and then finds if any of them are already in the group */
  override def getGroupByIdWithPageOfClientsToAdd(
    id: String,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(GroupSummary, PaginatedList[DisplayClient])]] =
    accessGroupsRepository
      .findById(id)
      .flatMap {
        case None => Future successful None
        case Some(grp) =>
          val enrolmentKeys = grp.clients.map(_.map(_.enrolmentKey))
          userClientDetailsConnector
            .getPaginatedClients(grp.arn)(page, pageSize, search, filter)
            .map { paginatedClients =>
              val paginatedList = PaginatedList[DisplayClient](
                paginationMetaData = paginatedClients.paginationMetaData,
                // TODO: NEED TO ONLY LOAD THE CLIENTS FROM THE GROUP THAT ARE IN THIS PAGINATED LIST
                pageContent = paginatedClients.pageContent.map { c =>
                  DisplayClient.fromClient(c, enrolmentKeys.getOrElse(Set.empty[String]).contains(c.enrolmentKey))
                }
              )
              Some((GroupSummary(grp._id.toString, grp.groupName, None, grp.teamMembers.size, None), paginatedList))
            }
      }

  override def create(
    accessGroup: CustomGroup
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

  override def getAllCustomGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]] =
    accessGroupsRepository
      .get(arn)
      .flatMap(withClientNames)

  override def get(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]] =
    accessGroupsRepository
      .get(groupId.arn, groupId.groupName)
      .flatMap(withClientName)

  override def getCustomGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    accessGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.clients.fold(false)(_.map(_.enrolmentKey).contains(enrolmentKey)))
          .map(GroupSummary.fromAccessGroup)
      )

  override def getCustomGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    accessGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.teamMembers.fold(false)(_.map(_.id).contains(userId)))
          .map(GroupSummary.fromAccessGroup)
      )

  override def delete(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupDeletionStatus] =
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

  override def update(groupId: GroupId, accessGroup: CustomGroup, whoIsUpdating: AgentUser)(implicit
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

  /* TODO APB-7066: updates whoIsUpdating (via repo?)
   *   + get client & team members in group to calculate assignments
   *   + push maybe assignments
   *   + auditing update
   * */
  def removeClient(groupId: String, clientId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    accessGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeClients = accessGroup.clients.map(_.filterNot(tm => tm.enrolmentKey == clientId))
          val clientToRemove = accessGroup.clients.map(_.filter(tm => tm.enrolmentKey == clientId)).get
          val usersInGroup = accessGroup.teamMembers.getOrElse(Set.empty)

          val updatedGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            clients = maybeClients
          )
          for {
            maybeCalculatedAssignments <-
              userEnrolmentAssignmentService
                .calculateForRemoveFromGroup(
                  GroupId(accessGroup.arn, accessGroup.groupName),
                  clientToRemove,
                  usersInGroup
                )
            maybeUpdatedCount <- accessGroupsRepository.update(accessGroup.arn, accessGroup.groupName, updatedGroup)
            updateStatus <- maybeUpdatedCount match {
                              case Some(updatedCount) =>
                                if (updatedCount == 1) {
                                  for {
                                    pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                    _ <- Future successful auditService.auditAccessGroupUpdate(
                                           updatedGroup
                                         ) // TODO update audit?
                                  } yield pushStatus match {
                                    case AssignmentsPushed =>
                                      AccessGroupUpdated
                                    case AssignmentsNotPushed =>
                                      AccessGroupUpdatedWithoutAssignmentsPushed
                                  }
                                } else {
                                  Future successful AccessGroupNotUpdated
                                }
                              case _ => Future successful AccessGroupNotUpdated
                            }
          } yield updateStatus
        case _ => Future successful AccessGroupNotUpdated
      }

  /* TODO move generic function calls when access group found
   *  change how whoIsUpdated happens (via repo? - needs encrypting...)
   *  update auditing, auditService.auditAccessGroupUpdate(updatedGroup) instead of whole group, log NET change (userToRemove)
   * */
  def removeTeamMember(groupId: String, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    accessGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeAgentUsers = accessGroup.teamMembers.map(_.filterNot(tm => tm.id == teamMemberId))
          val userToRemove = accessGroup.teamMembers.map(_.filter(tm => tm.id == teamMemberId)).get
          val clientsInGroup = accessGroup.clients.getOrElse(Set.empty)

          val updatedGroup = accessGroup.copy(
            lastUpdated = LocalDateTime.now(),
            lastUpdatedBy = whoIsUpdating,
            teamMembers = maybeAgentUsers
          )
          for {
            maybeCalculatedAssignments <-
              userEnrolmentAssignmentService
                .calculateForRemoveFromGroup(
                  GroupId(accessGroup.arn, accessGroup.groupName),
                  clientsInGroup,
                  userToRemove
                )
            maybeUpdatedCount <- accessGroupsRepository.update(accessGroup.arn, accessGroup.groupName, updatedGroup)
            updateStatus <- maybeUpdatedCount match {
                              case Some(updatedCount) =>
                                if (updatedCount == 1) {
                                  for {
                                    pushStatus <- pushAssignments(maybeCalculatedAssignments)
                                    _ <- Future successful auditService.auditAccessGroupUpdate(
                                           updatedGroup
                                         ) // TODO update audit?
                                  } yield pushStatus match {
                                    case AssignmentsPushed =>
                                      AccessGroupUpdated
                                    case AssignmentsNotPushed =>
                                      AccessGroupUpdatedWithoutAssignmentsPushed
                                  }
                                } else {
                                  Future successful AccessGroupNotUpdated
                                }
                              case _ => Future successful AccessGroupNotUpdated
                            }
          } yield updateStatus
        case _ => Future successful AccessGroupNotUpdated
      }

//  def attemptPushAssignments(maybeUpdatedCount: Option[Long], maybeCalculatedAssignments: Option[UserEnrolmentAssignments]) =
//    maybeUpdatedCount match {
//    case Some(updatedCount) =>
//      if (updatedCount == 1) {
//        for {
//          pushStatus <- pushAssignments(maybeCalculatedAssignments)
//        } yield pushStatus match {
//          case AssignmentsPushed =>
//            AccessGroupUpdated
//          case AssignmentsNotPushed =>
//            AccessGroupUpdatedWithoutAssignmentsPushed
//        }
//      } else {
//        Future successful AccessGroupNotUpdated
//      }
//    case _ => Future successful AccessGroupNotUpdated
//  }
//

  // TODO move below to groups summary service
  override def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList] =
    for {
      clients      <- userClientDetailsConnector.getClients(arn).map(_.toSet.flatten)
      accessGroups <- if (clients.nonEmpty) accessGroupsRepository.get(arn) else Future.successful(Seq.empty)
      enrolmentKeysInCustomGroups = accessGroups.flatMap(_.clients).toSet.flatten.map(_.enrolmentKey)
      taxServiceGroups <- taxGroupsService.getAllTaxServiceGroups(arn)
    } yield clients.foldLeft(ClientList(Set.empty, Set.empty)) { (clientList, client) =>
      val serviceKey = EnrolmentKey.deconstruct(client.enrolmentKey) match {
        case ("HMRC-TERS-ORG", _) | ("HMRC-TERSNT-ORG", _) =>
          "HMRC-TERS" // both types of trusts are represented by the same key in tax service groups
        case (sk, _) => sk
      }
      // The client is considered 'assigned' if: ...
      if (
        enrolmentKeysInCustomGroups.contains(client.enrolmentKey) || // ... they are in a custom access group, OR ...
        taxServiceGroups.exists(tsg => // ... there is a tax service group AND they are not excluded from it.
          tsg.service == serviceKey &&
            !tsg.excludedClients.getOrElse(Set.empty).exists(_.enrolmentKey == client.enrolmentKey)
        )
      ) {
        clientList.copy(assigned = clientList.assigned + client)
      } else {
        clientList.copy(unassigned = clientList.unassigned + client)
      }
    }

  override def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.assigned)

  override def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.unassigned)
  // TODO move above to groups summary service

  private def mergeWhoIsUpdating(accessGroup: CustomGroup, whoIsUpdating: AgentUser): Future[CustomGroup] =
    Future.successful(accessGroup.copy(lastUpdated = LocalDateTime.now(), lastUpdatedBy = whoIsUpdating))

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

  private def withClientNamesRemoved(accessGroup: CustomGroup): CustomGroup =
    accessGroup.copy(clients = accessGroup.clients.map(_.map(_.copy(friendlyName = ""))))

  private def withClientName(
    maybeAccessGroup: Option[CustomGroup]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomGroup]] =
    maybeAccessGroup.fold(Future successful Option.empty[CustomGroup])(accessGroup =>
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
    accessGroups: Seq[CustomGroup]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CustomGroup]] =
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

  override def addMemberToGroup(groupId: String, teamMember: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    accessGroupsRepository
      .addTeamMember(groupId, teamMember)
      .map(_.getMatchedCount match {
        case 1 => AccessGroupUpdatedWithoutAssignmentsPushed // TODO push assignments
        case _ => AccessGroupNotUpdated
      })
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
