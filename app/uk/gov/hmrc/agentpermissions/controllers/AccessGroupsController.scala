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
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Enrolment}
import uk.gov.hmrc.agentpermissions.service._
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
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some(AuthorisedAgent(arnIdentified, agentUser)) =>
          if (!Arn.isValid(arn.value)) {
            logger.info("Provided ARN is not valid")
            badRequestInvalidArn(arn)
          } else if (arn != arnIdentified) {
            logger.info("Provided ARN did not match with that identified by auth")
            Future.successful(BadRequest)
          } else {
            processAccessGroupCreation(arn, agentUser)
          }
      } transformWith handleFailure
  }

  def groupsSummaries(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some(AuthorisedAgent(arnIdentified, _)) =>
          if (!Arn.isValid(arn.value)) {
            badRequestInvalidArn(arn)
          } else if (arn != arnIdentified) {
            logger.info("Provided ARN did not match with that identified by auth")
            Future.successful(BadRequest)
          } else {
            accessGroupsService.groupSummaries(arn) flatMap { groupSummaries =>
              if (groupSummaries.isEmpty) {
                Future.successful(NotFound)
              } else {
                Future.successful(Ok(Json.toJson(groupSummaries)))
              }
            }
          }
      } transformWith handleFailure
  }

  def getGroup(gid: String): Action[AnyContent] = Action.async { implicit request =>
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some(AuthorisedAgent(arnIdentified, _)) =>
          GroupId.decode(gid) match {
            case None =>
              Future.successful(BadRequest("Check provided group id"))
            case Some(groupId) =>
              if (groupId.arn != arnIdentified) {
                logger.info("ARN obtained from provided group id did not match with that identified by auth")
                Future.successful(BadRequest)
              } else {
                accessGroupsService.get(groupId) flatMap {
                  case None =>
                    Future.successful(NotFound)
                  case Some(accessGroup) =>
                    Future.successful(Ok(Json.toJson(accessGroup)))
                }
              }
          }
      } transformWith handleFailure
  }

  def renameGroup(gid: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some(AuthorisedAgent(arnIdentified, agentUser)) =>
          GroupId.decode(gid) match {
            case None =>
              Future.successful(BadRequest("Check provided group id"))
            case Some(groupId) =>
              if (groupId.arn != arnIdentified) {
                logger.info("ARN obtained from provided group id did not match with that identified by auth")
                Future.successful(BadRequest)
              } else {
                processAccessGroupRename(groupId, agentUser)
              }
          }
      } transformWith handleFailure
  }

  private def processAccessGroupCreation(arn: Arn, agentUser: AgentUser)(implicit
    request: Request[JsValue]
  ): Future[Result] =
    request.body
      .validate[CreateAccessGroupRequest]
      .fold(
        errors => badRequestJsonParsing(errors),
        createAccessGroupRequest =>
          if (createAccessGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            accessGroupsService.create(buildAccessGroup(arn, createAccessGroupRequest, agentUser)) flatMap {
              case AccessGroupExists =>
                logger.info("Cannot create a group with a name that already exists")
                Future.successful(Conflict)
              case AccessGroupCreated(groupId) =>
                logger.info(s"Created group for '${arn.value}': '$groupId'")
                Future.successful(Created(JsString(groupId)))
              case AccessGroupNotCreated =>
                logger.warn("Unable to create access group")
                Future.successful(InternalServerError)
            }
          }
      )

  private def buildAccessGroup(
    arn: Arn,
    createAccessGroupRequest: CreateAccessGroupRequest,
    agentUser: AgentUser
  ): AccessGroup = {
    val now = LocalDateTime.now()

    AccessGroup(
      arn,
      createAccessGroupRequest.groupName,
      now,
      now,
      agentUser,
      agentUser,
      createAccessGroupRequest.teamMembers,
      createAccessGroupRequest.clients
    )
  }

  private def processAccessGroupRename(
    groupId: GroupId,
    agentUser: AgentUser
  )(implicit
    request: Request[JsValue]
  ): Future[Result] =
    request.body
      .validate[RenameAccessGroupRequest]
      .fold(
        errors => badRequestJsonParsing(errors),
        renameAccessGroupRequest =>
          if (renameAccessGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            accessGroupsService.rename(groupId, renameAccessGroupRequest.groupName, agentUser) flatMap {
              case AccessGroupNotExists =>
                logger.info("Cannot rename a group that does not already exist")
                Future.successful(NotFound)
              case AccessGroupNotRenamed =>
                logger.warn("Unable to rename access group")
                Future.successful(NotModified)
              case AccessGroupRenamed =>
                logger.warn("Renamed access group")
                Future.successful(Ok)
            }
          }
      )

  private def badRequestInvalidArn(arn: Arn): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsString(s"Invalid arn value: '${arn.value}' provided"))))

  private def badRequestJsonParsing(errors: Seq[(JsPath, Seq[JsonValidationError])]): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))

  private def badRequestGroupNameMaxLength: Future[Result] =
    Future.successful(
      BadRequest(Json.obj("message" -> JsString(s"Group name length exceeds maximum allowed $MAX_LENGTH_GROUP_NAME")))
    )

  private def handleFailure(t: Try[Result]): Future[Result] = t match {
    case Success(result) =>
      Future.successful(result)
    case Failure(ex) =>
      logger.error(s"Error processing request: ${ex.getMessage}")
      Future.successful(InternalServerError)
  }
}

case class CreateAccessGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Enrolment]]
)

object CreateAccessGroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[CreateAccessGroupRequest] = Json.format[CreateAccessGroupRequest]
}

case class RenameAccessGroupRequest(groupName: String)

object RenameAccessGroupRequest {

  implicit val reads: Reads[RenameAccessGroupRequest] = for {
    groupName <- (JsPath \ "group-name").read[String]
  } yield RenameAccessGroupRequest(groupName)

  implicit val writes: Writes[RenameAccessGroupRequest] = Json.writes[RenameAccessGroupRequest]

  implicit val formatRenameAccessGroupRequest: Format[RenameAccessGroupRequest] = Format(reads, writes)
}
