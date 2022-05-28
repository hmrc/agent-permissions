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
import uk.gov.hmrc.agentpermissions.service.{AccessGroupCreated, AccessGroupExists, AccessGroupNotCreated, AccessGroupsService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AccessGroupsController @Inject() (accessGroupsService: AccessGroupsService)(implicit
  cc: ControllerComponents,
  val ec: ExecutionContext
) extends BackendController(cc) with Logging {

  private val MAX_LENGTH_GROUP_NAME = 32

  def createGroup(arn: Arn): Action[JsValue] = Action.async(parse.json) { implicit request =>
    if (!Arn.isValid(arn.value)) {
      badRequestInvalidArn(arn)
    } else {
      request.body
        .validate[CreateAccessGroupRequest]
        .fold(
          errors => badRequestJsonParsing(errors),
          createAccessGroupRequest =>
            if (createAccessGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
              badRequestGroupNameMaxLength
            } else {
              processAccessGroupCreation(arn, createAccessGroupRequest)
                .recoverWith(handleFailure)
            }
        )
    }
  }

  private def processAccessGroupCreation(arn: Arn, createAccessGroupRequest: CreateAccessGroupRequest): Future[Result] =
    accessGroupsService
      .create(buildAccessGroup(arn, createAccessGroupRequest))
      .map {
        case AccessGroupExists =>
          logger.info("Cannot create a group with a name that already exists")
          Conflict
        case AccessGroupCreated(creationId) =>
          Created(JsString(creationId))
        case AccessGroupNotCreated =>
          logger.warn("Unable to create access group")
          InternalServerError
      }

  private def buildAccessGroup(arn: Arn, createAccessGroupRequest: CreateAccessGroupRequest): AccessGroup = {
    val now = LocalDateTime.now()

    AccessGroup(
      arn,
      createAccessGroupRequest.groupName,
      now,
      now,
      createAccessGroupRequest.createdBy,
      createAccessGroupRequest.createdBy,
      createAccessGroupRequest.teamMembers,
      createAccessGroupRequest.clients
    )
  }

  private def badRequestInvalidArn(arn: Arn): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsString(s"Invalid arn value: '${arn.value}' provided"))))

  private def badRequestJsonParsing(errors: Seq[(JsPath, Seq[JsonValidationError])]): Future[Result] =
    Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))

  private def badRequestGroupNameMaxLength: Future[Result] =
    Future.successful(
      BadRequest(Json.obj("message" -> JsString(s"Group name length exceeds maximum allowed $MAX_LENGTH_GROUP_NAME")))
    )

  private def handleFailure: PartialFunction[Throwable, Future[Result]] = { case ex =>
    logger.info(s"Returning $InternalServerError: ${ex.getMessage}")
    Future.successful(InternalServerError)
  }
}

case class CreateAccessGroupRequest(
  groupName: String,
  teamMembers: Option[Set[AgentUser]],
  clients: Option[Set[Enrolment]],
  createdBy: AgentUser
)

object CreateAccessGroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[CreateAccessGroupRequest] = Json.format[CreateAccessGroupRequest]
}
