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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.model.{AddMembersToTaxServiceGroupRequest, AddOneTeamMemberToGroupRequest, CreateTaxServiceGroupRequest, UpdateTaxServiceGroupRequest}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.service._
import uk.gov.hmrc.agents.accessgroups.{GroupSummary, TaxGroup}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton()
class TaxServiceGroupsController @Inject() (taxGroupsService: TaxGroupsService)(implicit
  authAction: AuthAction,
  cc: ControllerComponents,
  val ec: ExecutionContext
) extends BackendController(cc) with AuthorisedAgentSupport {

  private val MAX_LENGTH_GROUP_NAME = 50 // 32

  def createGroup(arn: Arn): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { matchedArn =>
        withJsonParsed[CreateTaxServiceGroupRequest] { createGroupRequest =>
          if (createGroupRequest.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            for {
              groupCreationStatus <-
                taxGroupsService
                  .create(createGroupRequest.buildTaxServiceGroup(matchedArn, authorisedAgent.agentUser))
            } yield groupCreationStatus match {
              case TaxServiceGroupExistsForCreation =>
                logger.info("Cannot create a tax service group with a name that already exists")
                Conflict
              case TaxServiceGroupCreated(groupId) =>
                logger.info(s"Created tax service group for '${matchedArn.value}': '$groupId'")
                Created(JsString(groupId))
              case TaxServiceGroupNotCreated =>
                logger.warn("Unable to create access group")
                InternalServerError
              case unknownStatus =>
                logger.warn(s"Unknown status during group creation: $unknownStatus")
                InternalServerError
            }
          }
        }
      }
    } transformWith failureHandler
  }

  // gets access group summaries for tax service groups ONLY, without client count
  def groups(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { _ =>
        taxGroupsService
          .getAllTaxServiceGroups(arn)
          .map(groups => Ok(Json.toJson(groups.map(group => GroupSummary.of(group)))))
      }
    } transformWith failureHandler
  }

  // gets a tax service group ONLY
  def getGroup(gid: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      taxGroupsService.getById(gid) map {
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

  def getGroupByService(arn: Arn, service: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      taxGroupsService.getByService(arn, service).map {
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

  def deleteGroup(gid: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withTaxGroup(gid, authorisedAgent.arn) { taxGroup =>
        for {
          groupDeletionStatus <- taxGroupsService.delete(taxGroup.arn, taxGroup.groupName, authorisedAgent.agentUser)
        } yield groupDeletionStatus match {
          case TaxServiceGroupNotDeleted =>
            logger.info("Access group was not deleted")
            NotModified
          case TaxServiceGroupDeleted =>
            Ok
        }
      }
    } transformWith failureHandler
  }

  def updateGroup(gid: GroupId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withJsonParsed[UpdateTaxServiceGroupRequest] { updateGroupRequest =>
        withTaxGroup(gid, authorisedAgent.arn) { existingAccessGroup =>
          val mergedAccessGroup = updateGroupRequest.merge(existingAccessGroup)

          if (mergedAccessGroup.groupName.length > MAX_LENGTH_GROUP_NAME) {
            badRequestGroupNameMaxLength
          } else {
            taxGroupsService.update(
              existingAccessGroup.arn,
              existingAccessGroup.groupName,
              mergedAccessGroup,
              authorisedAgent.agentUser
            ) map {
              case TaxServiceGroupNotUpdated =>
                logger.info("Access group was not updated")
                NotFound
              case TaxServiceGroupUpdated =>
                Ok
              case unknown =>
                logger.warn(s"Unknown status during group update: $unknown")
                InternalServerError
            }
          }
        }
      }
    } transformWith failureHandler
  }

  def addMembers(gid: GroupId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withJsonParsed[AddMembersToTaxServiceGroupRequest] { updateGroupRequest =>
        withTaxGroup(gid, authorisedAgent.arn) { group =>
          val groupWithExcludedClientsAdded = updateGroupRequest.excludedClients.fold(group)(enrolments =>
            group.copy(excludedClients = group.excludedClients ++ enrolments)
          )
          val groupWithTeamMembersAdded = updateGroupRequest.teamMembers.fold(groupWithExcludedClientsAdded)(tms =>
            group.copy(teamMembers = group.teamMembers ++ tms)
          )
          taxGroupsService.update(
            group.arn,
            group.groupName,
            groupWithTeamMembersAdded,
            authorisedAgent.agentUser
          ) map {
            case TaxServiceGroupNotUpdated =>
              logger.info(s"Access group '${group.groupName}' was not updated")
              NotFound
            case TaxServiceGroupUpdated =>
              Ok
            case unknown =>
              logger.warn(s"Unknown status during group update: $unknown")
              InternalServerError
          }
        }
      }
    } transformWith failureHandler
  }

  def addTeamMemberToGroup(gid: GroupId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withAuthorisedAgent() { _ =>
      withJsonParsed[AddOneTeamMemberToGroupRequest] { addRequest =>
        taxGroupsService.addMemberToGroup(gid, addRequest.teamMember) map {
          case TaxServiceGroupNotUpdated =>
            logger.info(s"Tax Service Group with id '$gid' was not updated it probably doesn't exist")
            NotFound
          case TaxServiceGroupUpdated => Ok
        }
      }
    }
  }

  def removeTeamMember(gid: GroupId, memberId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      taxGroupsService
        .removeTeamMember(gid, memberId, authorisedAgent.agentUser)
        .map {
          case TaxServiceGroupNotUpdated =>
            logger.info(s"Tax group '$gid' didn't remove member '$memberId''")
            NotModified
          case TaxServiceGroupUpdated =>
            logger.info(s"Tax group removed a team member")
            NoContent
        }
    }
  }

  def clientCountForAvailableTaxServices(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { _ =>
        taxGroupsService
          .clientCountForAvailableTaxServices(arn)
          .map(clientCount => Ok(Json.toJson(clientCount)))
      }
    } transformWith failureHandler
  }

  def clientCountForTaxGroups(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { authorisedAgent =>
      withValidAndMatchingArn(arn, authorisedAgent) { _ =>
        taxGroupsService
          .clientCountForTaxGroups(arn)
          .map(clientCount => Ok(Json.toJson(clientCount)))
      }
    } transformWith failureHandler
  }

  // TODO move to separate GroupAction
  private def withTaxGroup(gid: GroupId, authorisedArn: Arn)(
    body: TaxGroup => Future[Result]
  )(implicit hc: HeaderCarrier): Future[Result] =
    taxGroupsService.getById(gid) flatMap {
      case None =>
        logger.warn(s"Group not found for '$gid', cannot update")
        Future.successful(BadRequest(s"Check provided gid '$gid"))
      case Some(accessGroup) =>
        if (accessGroup.arn != authorisedArn) {
          logger.warn("ARN obtained from provided group id did not match with that identified by auth")
          Future.successful(Forbidden)
        } else {
          body(accessGroup)
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

  private def badRequestJsonParsing(
    errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]
  ): Future[Result] =
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
