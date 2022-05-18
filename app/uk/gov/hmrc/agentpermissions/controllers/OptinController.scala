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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedIn, OptedOut, OptinStatus}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service.OptinService
import uk.gov.hmrc.auth.core.NoActiveSession
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class OptinController @Inject() (optinService: OptinService, authAction: AuthAction)(implicit
  val appConfig: AppConfig,
  cc: ControllerComponents,
  val ec: ExecutionContext
) extends BackendController(cc) with Logging {

  def optin(arnProvided: Arn): Action[AnyContent] = Action.async { implicit request =>
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some((arnIdentified, user)) =>
          if (arnProvided == arnIdentified) {
            optinService.optin(arnIdentified, user) flatMap {
              case None =>
                logger.info(s"Already has $OptedIn")
                Future.successful(Conflict)
              case Some(upsertType) =>
                logger.info(s"Optin record created, type: $upsertType")
                Future.successful(Created)
            }
          } else {
            logger.info("Provided ARN did not match with that identified by auth")
            Future.successful(BadRequest)
          }
      }
      .recover(handleFailure)
  }

  def optout(arnProvided: Arn): Action[AnyContent] = Action.async { implicit request =>
    authAction
      .getAuthorisedAgent()
      .flatMap {
        case None =>
          logger.info("Could not identify authorised agent")
          Future.successful(Forbidden)
        case Some((arnIdentified, user)) =>
          if (arnProvided == arnIdentified) {
            optinService.optout(arnIdentified, user) flatMap {
              case None =>
                logger.info(s"Already has $OptedOut")
                Future.successful(Conflict)
              case Some(upsertType) =>
                logger.info(s"Optin record created, type: $upsertType")
                Future.successful(Created)
            }
          } else {
            logger.info("Provided ARN did not match with that identified by auth")
            Future.successful(BadRequest)
          }
      }
      .recover(handleFailure)
  }

  def optinStatus(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    optinService
      .optinStatus(arn)
      .map(generateStatusJson)
      .map {
        case None             => NotFound
        case Some(statusJson) => Ok(statusJson)
      }
      .recover { case ex =>
        logger.info(s"Returning $InternalServerError: ${ex.getMessage}")
        InternalServerError
      }
  }

  private def generateStatusJson(maybeOptinStatus: Option[OptinStatus]): Option[JsValue] =
    maybeOptinStatus.map(optinStatus => Json.toJson(Json.obj("status" -> optinStatus.value)))

  private def handleFailure: PartialFunction[Throwable, Result] = {
    case ex: NoActiveSession =>
      logger.info(s"Returning $Forbidden: ${ex.getMessage}")
      Forbidden
    case ex =>
      logger.info(s"Returning $InternalServerError: ${ex.getMessage}")
      InternalServerError
  }
}
