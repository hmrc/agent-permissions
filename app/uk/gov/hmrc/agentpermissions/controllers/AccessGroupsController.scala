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

package uk.gov.hmrc.agentpermissions.controllers

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.agentpermissions.model.{AddMembersToAccessGroupRequest, AddOneTeamMemberToGroupRequest, CreateAccessGroupRequest, UpdateAccessGroupRequest}
import uk.gov.hmrc.agentpermissions.service._
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton()
class AccessGroupsController @Inject() (
  accessGroupsService: AccessGroupsService, // TODO rename to customGroupsService
  groupsService: GroupsService,
  eacdSynchronizer: EacdSynchronizer
)(implicit authAction: AuthAction, cc: ControllerComponents, val ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedAgentSupport {

  private val MAX_LENGTH_GROUP_NAME = 50 // 32

  // checks all group names for duplicates
  def groupNameCheck(arn: Arn, name: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        for {
          groups <- groupsService.getAllGroupSummaries(matchedArn)
        } yield
          if (groups.exists(_.groupName.equalsIgnoreCase(Option(name).map(_.trim).getOrElse("")))) {
            Conflict
          } else {
            Ok
          }
      }
    } transformWith failureHandler
  }

  // sorted by group name A-Z
  def getAllGroupSummaries(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        for {
          combinedSorted <- groupsService.getAllGroupSummaries(matchedArn)
        } yield Ok(Json.toJson(combinedSorted))
      }
    } transformWith failureHandler
  }

  // gets all group summaries for client
  def getGroupSummariesForClient(arn: Arn, enrolmentKey: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthorisedAgent(allowStandardUser = true) { _ =>
        groupsService
          .getAllGroupSummariesForClient(arn, enrolmentKey)
          .map(result => if (result.isEmpty) NotFound else Ok(Json.toJson(result)))
      } transformWith failureHandler
  }

  // gets all group summaries for team member
  def getGroupSummariesForTeamMember(arn: Arn, userId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { _ =>
      groupsService
        .getAllGroupSummariesForTeamMember(arn, userId)
        .map(result => if (result.isEmpty) NotFound else Ok(Json.toJson(result)))
    } transformWith failureHandler
  }

  private def filterBySearchTermAndService(
    clients: Set[Client],
    search: Option[String],
    filter: Option[String]
  ): Set[Client] = {
    val clientsMatchingSearch = search.fold(clients) { searchTerm =>
      clients.filter(c => c.friendlyName.toLowerCase.contains(searchTerm.toLowerCase))
    }
    val taxServiceFilteredClients = filter.fold(clientsMatchingSearch) { term =>
      if (term == "TRUST") clientsMatchingSearch.filter(_.enrolmentKey.contains("HMRC-TERS"))
      else clientsMatchingSearch.filter(_.enrolmentKey.contains(term))
    }
    taxServiceFilteredClients
  }

  def unassignedClients(
    arn: Arn,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { _ =>
        for {
          unfilteredClients <- accessGroupsService.getUnassignedClients(arn)
          filteredClients = filterBySearchTermAndService(unfilteredClients, search, filter)
          sortedClients = filteredClients.toSeq.sortBy(c => c.friendlyName.toLowerCase)
        } yield Ok(
          Json.toJson(
            PaginatedListBuilder.build[Client](
              page,
              pageSize,
              sortedClients
            )
          )
        )
      } transformWith failureHandler
    }
  }

  def createGroup(arn: Arn): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        withJsonParsed[CreateAccessGroupRequest] { createAccessGroupRequest =>
          if (createAccessGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            for {
              groupCreationStatus <-
                accessGroupsService
                  .create(createAccessGroupRequest.buildAccessGroup(matchedArn, authorisedAgent.agentUser))
            } yield groupCreationStatus match {
              case AccessGroupExistsForCreation =>
                logger.info("Cannot create a group with a name that already exists")
                Conflict
              case AccessGroupCreated(groupId) =>
                logger.info(s"Created group for '${matchedArn.value}': '$groupId'")
                Created(JsString(groupId))
              case AccessGroupCreatedWithoutAssignmentsPushed(groupId) =>
                logger.warn(s"Created group for '${matchedArn.value}': '$groupId', but assignments were not pushed")
                Created(JsString(groupId))
              case AccessGroupNotCreated =>
                logger.warn("Unable to create access group")
                InternalServerError
            }
          }
        }
      }
    } transformWith failureHandler
  }

  // gets access group summaries for custom groups ONLY
  def groups(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { _ =>
        accessGroupsService
          .getAllCustomGroups(arn)
          .map(groups => Ok(Json.toJson(groups.map(GroupSummary.fromAccessGroup))))
      }
    } transformWith failureHandler
  }

  // gets a custom access group ONLY
  @deprecated("group could be too big with 5000+ clients - use getCustomGroupSummary & paginated lists instead")
  def getGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      accessGroupsService.getById(gid) map {
        case None =>
          NotFound
        case Some(accessGroup) =>
          if (accessGroup.arn != authorisedAgent.arn) {
            logger.info("ARN obtained from provided group id did not match with that identified by auth")
            Forbidden
          } else {
            Ok(Json.toJson(accessGroup))
          }
      }
    } transformWith failureHandler
  }

  def getCustomGroupSummary(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      accessGroupsService.getById(gid) map {
        case None =>
          NotFound
        case Some(accessGroup) =>
          if (accessGroup.arn != authorisedAgent.arn) {
            logger.info("ARN obtained from provided group id did not match with that identified by auth")
            Forbidden
          } else {
            Ok(Json.toJson(GroupSummary.fromAccessGroup(accessGroup)))
          }
      }
    } transformWith failureHandler
  }

  def getPaginatedClientsForGroup(
    gid: String,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      accessGroupsService.getById(gid) map {
        case None =>
          NotFound
        case Some(accessGroup) =>
          if (accessGroup.arn != authorisedAgent.arn) {
            logger.info("ARN obtained from provided group id did not match with that identified by auth")
            Forbidden
          } else {
            val groupClients = accessGroup.clients.getOrElse(Set.empty)
            val clientsMatchingSearch = search.fold(groupClients) { searchTerm =>
              groupClients.filter(c => c.friendlyName.toLowerCase.contains(searchTerm.toLowerCase))
            }
            val taxServiceFilteredClients = filter.fold(clientsMatchingSearch) { term =>
              if (term == "TRUST") clientsMatchingSearch.filter(_.enrolmentKey.contains("HMRC-TERS"))
              else clientsMatchingSearch.filter(_.enrolmentKey.contains(term))
            }
            val filteredClientsPage = taxServiceFilteredClients.toSeq.sortBy(c => c.friendlyName.toLowerCase)
            Ok(Json.toJson(PaginatedListBuilder.build[Client](page, pageSize, filteredClientsPage)))
          }
      }
    } transformWith failureHandler
  }

  def getPaginatedClientsForAddingToGroup(
    gid: String,
    page: Int = 1,
    pageSize: Int = 20,
    search: Option[String] = None,
    filter: Option[String] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      accessGroupsService
        .getGroupByIdWithPageOfClientsToAdd(gid, page, pageSize, search, filter)
        .map {
          case None => NotFound
          case Some((groupSummary, clients)) =>
            Ok(Json.toJson(groupSummary, clients))
        }
    } transformWith failureHandler
  }

  def deleteGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withGroupId(gid, authorisedAgent.arn) { (groupId, _) =>
        for {
          groupDeletionStatus <- accessGroupsService.delete(groupId, authorisedAgent.agentUser)
        } yield groupDeletionStatus match {
          case AccessGroupNotDeleted =>
            logger.info("Access group was not deleted")
            NotModified
          case AccessGroupDeleted =>
            Ok
          case AccessGroupDeletedWithoutAssignmentsPushed =>
            logger.warn(s"Access group was deleted, but assignments were not pushed")
            Ok
        }
      }
    } transformWith failureHandler
  }

  def updateGroup(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withJsonParsed[UpdateAccessGroupRequest] { updateAccessGroupRequest =>
        withGroupId(gid, authorisedAgent.arn) { (groupId, existingAccessGroup) =>
          val mergedAccessGroup = updateAccessGroupRequest.merge(existingAccessGroup)

          if (mergedAccessGroup.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            accessGroupsService.update(groupId, mergedAccessGroup, authorisedAgent.agentUser) map {
              case AccessGroupNotUpdated =>
                logger.info("Custom group was not updated")
                NotFound
              case AccessGroupUpdated =>
                Ok
              case AccessGroupUpdatedWithoutAssignmentsPushed =>
                logger.warn(s"Custom group was updated, but assignments were not pushed")
                Ok
            }
          }
        }
      }
    } transformWith failureHandler
  }

  def removeClient(gid: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      accessGroupsService
        .removeClient(gid, clientId, authorisedAgent.agentUser)
        .map {
          case AccessGroupNotUpdated =>
            logger.info(s"Custom group '$gid' didn't remove client '$clientId''")
            NotModified
//          case AccessGroupUpdated => NoContent
          case AccessGroupUpdatedWithoutAssignmentsPushed =>
            logger.info(s"Custom group removed a client, but assignments were not pushed")
            NoContent
        }
    }
  }

  def removeTeamMember(gid: String, memberId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      accessGroupsService
        .removeTeamMember(gid, memberId, authorisedAgent.agentUser)
        .map {
          case AccessGroupNotUpdated =>
            logger.info(s"Custom group '$gid' didn't remove member '$memberId''")
            NotModified
//          case AccessGroupUpdated => NoContent
          case AccessGroupUpdatedWithoutAssignmentsPushed =>
            logger.info(s"Custom group removed a team member, but assignments were not pushed")
            NoContent
        }
    }
  }

  def addUnassignedMembers(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withJsonParsed[AddMembersToAccessGroupRequest] { updateAccessGroupRequest =>
        withGroupId(gid, authorisedAgent.arn) { (groupId, group) =>
          val groupWithClientsAdded = updateAccessGroupRequest.clients.fold(group)(enrolments =>
            group.copy(clients = Some(group.clients.getOrElse(Set.empty) ++ enrolments))
          )
          val groupWithTeamMembersAdded = updateAccessGroupRequest.teamMembers.fold(groupWithClientsAdded)(tms =>
            group.copy(teamMembers = Some(group.teamMembers.getOrElse(Set.empty) ++ tms))
          )
          accessGroupsService.update(groupId, groupWithTeamMembersAdded, authorisedAgent.agentUser) map {
            case AccessGroupNotUpdated =>
              logger.info(s"Access group '${groupId.groupName}' was not updated")
              NotFound
            case AccessGroupUpdated =>
              Ok
            case AccessGroupUpdatedWithoutAssignmentsPushed =>
              logger.warn(s"Access group '${groupId.groupName}' was updated, but assignments were not pushed")
              Ok
          }
        }
      }
    } transformWith failureHandler
  }

  def addTeamMemberToGroup(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withJsonParsed[AddOneTeamMemberToGroupRequest] { addRequest =>
        accessGroupsService
          .addMemberToGroup(gid, addRequest.teamMember, authorisedAgent.agentUser) map {
//          case AccessGroupUpdated => Ok
          case AccessGroupUpdatedWithoutAssignmentsPushed =>
            logger.info(s"Custom group added a team member, but assignments were not pushed")
            Ok
          case AccessGroupNotUpdated =>
            logger.info(s"Custom group with id '$gid' was not updated it probably doesn't exist")
            NotFound
        }
      }
    }
  }

  def syncWithEacd(arn: Arn, fullSync: Boolean = false): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        // Note: we are not waiting for this future to complete before returning a response
        eacdSynchronizer.syncWithEacd(matchedArn, fullSync).onComplete {
          case Success(Some(updateStatuses)) =>
            logger.info(s"EACD Sync request processed for ${arn.value} with results: ${updateStatuses.mkString(", ")}")
          case Success(None) =>
            logger.info(s"EACD Sync request for ${arn.value} was not processed at this time.")
          case Failure(ex) =>
            logger.error(s"Error during EACD Sync for ${arn.value}: ${ex.getMessage}")
        }

        Future.successful(Accepted)
      }
    }.transformWith(failureHandler)
  }

  private def withGroupId(gid: String, authorisedArn: Arn)(
    body: (GroupId, CustomGroup) => Future[Result]
  )(implicit hc: HeaderCarrier): Future[Result] =
    accessGroupsService.getById(gid) flatMap {
      case None =>
        logger.warn(s"Group not found for '$gid', cannot update")
        Future.successful(BadRequest(s"Check provided gid '$gid"))
      case Some(accessGroup) =>
        val groupId = GroupId(accessGroup.arn, accessGroup.groupName)
        if (groupId.arn != authorisedArn) {
          logger.warn("ARN obtained from provided group id did not match with that identified by auth")
          Future.successful(Forbidden)
        } else {
          body(groupId, accessGroup)
        }
    }

  private def withValidAndMatchingArn(providedArn: Arn, authorisedAgent: AuthorisedAgent)(
    body: Arn => Future[Result]
  ): Future[Result] =
    if (!Arn.isValid(providedArn.value)) {
      logger.info("Provided ARN is not valid")
      badRequestInvalidArn(providedArn)
    } else if (providedArn != authorisedAgent.arn) {
      logger.info("Provided ARN did not match with that identified by auth")
      Future.successful(BadRequest)
    } else {
      body(providedArn)
    }

  def withJsonParsed[T](
    body: T => Future[Result]
  )(implicit request: Request[JsValue], reads: Reads[T]): Future[Result] =
    request.body
      .validate[T]
      .fold(
        errors => badRequestJsonParsing(errors),
        deserialized => body(deserialized)
      )

  private def badRequestInvalidArn(arn: Arn): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsString(s"Invalid arn value: '${arn.value}' provided"))))

  private def badRequestJsonParsing(errors: Seq[(JsPath, Seq[JsonValidationError])]): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))

  private def badRequestGroupNameMaxLength: Future[Result] =
    Future.successful(
      BadRequest(Json.obj("message" -> JsString(s"Group name length exceeds maximum allowed $MAX_LENGTH_GROUP_NAME")))
    )

  private def failureHandler(triedResult: Try[Result]): Future[Result] = triedResult match {
    case Success(result) =>
      Future.successful(result)
    case Failure(ex: AuthorisationException) =>
      logger.error(s"Auth error: ${ex.getMessage}")
      Future.successful(Forbidden)
    case Failure(ex) =>
      logger.error(s"Error processing request: ${ex.getMessage}")
      Future.successful(InternalServerError)
  }
}
