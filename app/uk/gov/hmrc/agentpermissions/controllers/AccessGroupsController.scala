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
      withGroupId(gid, authorisedAgent.arn) { groupId =>
        for {
          maybeAccessGroup <- accessGroupsService.get(groupId)
        } yield maybeAccessGroup match {
          case None =>
            NotFound
          case Some(accessGroup) =>
            Ok(Json.toJson(accessGroup))
        }
      }
    } transformWith failureHandler
  }

  def renameGroup(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withGroupId(gid, authorisedAgent.arn) { groupId =>
        withJsonParsed[RenameAccessGroupRequest] { renameAccessGroupRequest =>
          if (renameAccessGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            for {
              groupRenamingStatus <-
                accessGroupsService.rename(groupId, renameAccessGroupRequest.groupName, authorisedAgent.agentUser)
            } yield groupRenamingStatus match {
              case AccessGroupNotExistsForRenaming =>
                logger.info("Cannot rename a group that does not already exist")
                NotFound
              case AccessGroupNotRenamed =>
                logger.warn("Unable to rename access group")
                NotModified
              case AccessGroupRenamed =>
                logger.warn("Renamed access group")
                Ok
            }
          }
        }
      }
    } transformWith failureHandler
  }

  def deleteGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { authorisedAgent =>
      withGroupId(gid, authorisedAgent.arn) { groupId =>
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
      withGroupId(gid, authorisedAgent.arn) { groupId =>
        withJsonParsed[AccessGroup] { accessGroup =>
          if (accessGroup.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            for {
              groupUpdateStatus <- accessGroupsService.update(groupId, accessGroup, authorisedAgent.agentUser)
            } yield groupUpdateStatus match {
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
    body: GroupId => Future[Result]
  ): Future[Result] =
    GroupId.decode(gid) match {
      case None =>
        Future.successful(BadRequest("Check provided group id"))
      case Some(groupId) =>
        if (groupId.arn != authorisedArn) {
          logger.info("ARN obtained from provided group id did not match with that identified by auth")
          Future.successful(Forbidden)
        } else {
          body(groupId)
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

sealed trait AccessGroupRequest

case class CreateAccessGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Enrolment]]
) extends AccessGroupRequest {
  def buildAccessGroup(
    arn: Arn,
    agentUser: AgentUser
  ): AccessGroup = {
    val now = LocalDateTime.now()

    AccessGroup(
      arn,
      groupName,
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

case class RenameAccessGroupRequest(groupName: String) extends AccessGroupRequest

object RenameAccessGroupRequest {

  implicit val reads: Reads[RenameAccessGroupRequest] = for {
    groupName <- (JsPath \ "group-name").read[String]
  } yield RenameAccessGroupRequest(groupName)

  implicit val writes: Writes[RenameAccessGroupRequest] = Json.writes[RenameAccessGroupRequest]

  implicit val formatRenameAccessGroupRequest: Format[RenameAccessGroupRequest] = Format(reads, writes)
}
