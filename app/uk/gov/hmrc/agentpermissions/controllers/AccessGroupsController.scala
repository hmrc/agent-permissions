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

package uk.gov.hmrc.agentpermissions.controllers

import play.api.Logging
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.service._
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton()
class AccessGroupsController @Inject() (accessGroupsService: AccessGroupsService, authAction: AuthAction)(implicit
  cc: ControllerComponents,
  val ec: ExecutionContext
) extends BackendController(cc) with Logging {

  private val MAX_LENGTH_GROUP_NAME = 32

  def createGroup(arn: Arn): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
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

  def groupsSummaries(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        for {
          groups            <- accessGroupsService.getAllGroups(matchedArn)
          unassignedClients <- accessGroupsService.getUnassignedClients(arn)
          groupSummaries = AccessGroupSummaries(groups.map(AccessGroupSummary.convert), unassignedClients)
        } yield
          if (groupSummaries.groups.isEmpty && groupSummaries.unassignedClients.isEmpty) {
            NotFound
          } else {
            Ok(Json.toJson(groupSummaries))
          }
      }
    } transformWith failureHandler
  }

  def getGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
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

  def getGroupSummariesForClient(arn: Arn, enrolmentKey: String) = Action.async {
    {
      accessGroupsService
        .get(arn, enrolmentKey)
        .map(result => if (result.isEmpty) NotFound else Ok(Json.toJson(result)))
    } transformWith failureHandler
  }

  def deleteGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withGroupId(gid, authorisedAgent.arn) { (groupId, _) =>
        for {
          groupDeletionStatus <- accessGroupsService.delete(groupId)
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
    withAuthorisedAgent { authorisedAgent =>
      withJsonParsed[UpdateAccessGroupRequest] { updateAccessGroupRequest =>
        withGroupId(gid, authorisedAgent.arn) { (groupId, existingAccessGroup) =>
          val mergedAccessGroup = updateAccessGroupRequest.merge(existingAccessGroup)

          if (mergedAccessGroup.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            accessGroupsService.update(groupId, mergedAccessGroup, authorisedAgent.agentUser) map {
              case AccessGroupNotUpdated =>
                logger.info("Access group was not updated")
                NotFound
              case AccessGroupUpdated =>
                Ok
              case AccessGroupUpdatedWithoutAssignmentsPushed =>
                logger.warn(s"Access group was updated, but assignments were not pushed")
                Ok
            }
          }
        }
      }
    } transformWith failureHandler
  }

  def addUnassignedMembers(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
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
              logger.info("Access group was not updated")
              NotFound
            case AccessGroupUpdated =>
              Ok
            case AccessGroupUpdatedWithoutAssignmentsPushed =>
              logger.warn(s"Access group was updated, but assignments were not pushed")
              Ok
          }
        }
      }
    } transformWith failureHandler
  }

  def groupNameCheck(arn: Arn, name: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        for {
          existingGroups <- accessGroupsService.getAllGroups(matchedArn)
        } yield
          if (existingGroups.exists(_.groupName.equalsIgnoreCase(Option(name).map(_.trim).getOrElse("")))) {
            Conflict
          } else {
            Ok
          }
      }
    } transformWith failureHandler
  }

  def syncWithEacd(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        accessGroupsService.syncWithEacd(matchedArn, authorisedAgent.agentUser).map(_ => Ok)
      }
    } transformWith failureHandler
  }

  private def withAuthorisedAgent[T](
    body: AuthorisedAgent => Future[Result]
  )(implicit request: Request[T], ec: ExecutionContext): Future[Result] =
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some(authorisedAgent: AuthorisedAgent) =>
          body(authorisedAgent)
      }

  private def withGroupId(gid: String, authorisedArn: Arn)(
    body: (GroupId, AccessGroup) => Future[Result]
  ): Future[Result] =
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

case class CreateAccessGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Enrolment]]
) {
  def buildAccessGroup(
    arn: Arn,
    agentUser: AgentUser
  ): AccessGroup = {
    val now = LocalDateTime.now()

    AccessGroup(
      arn,
      Option(groupName).map(_.trim).getOrElse(""),
      now,
      now,
      agentUser,
      agentUser,
      teamMembers,
      clients
    )
  }
}

object CreateAccessGroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[CreateAccessGroupRequest] = Json.format[CreateAccessGroupRequest]
}

case class UpdateAccessGroupRequest(
  groupName: Option[String],
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Enrolment]]
) {

  def merge(existingAccessGroup: AccessGroup): AccessGroup = {
    val withMergedGroupName = groupName.fold(existingAccessGroup)(name =>
      existingAccessGroup.copy(groupName = Option(name).map(_.trim).getOrElse(""))
    )
    val withMergedClients = clients.fold(withMergedGroupName)(cls => withMergedGroupName.copy(clients = Some(cls)))
    teamMembers.fold(withMergedClients)(members => withMergedClients.copy(teamMembers = Some(members)))
  }
}

object UpdateAccessGroupRequest {
  implicit val format: OFormat[UpdateAccessGroupRequest] = Json.format[UpdateAccessGroupRequest]
}

case class AddMembersToAccessGroupRequest(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Enrolment]])

object AddMembersToAccessGroupRequest {
  implicit val format: OFormat[AddMembersToAccessGroupRequest] = Json.format[AddMembersToAccessGroupRequest]
}
