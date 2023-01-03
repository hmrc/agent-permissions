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

import play.api.libs.json.JsString
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedIn, OptedOut}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service.OptinService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton()
class OptinController @Inject() (optinService: OptinService)(implicit
  authAction: AuthAction,
  val appConfig: AppConfig,
  cc: ControllerComponents,
  val ec: ExecutionContext
) extends BackendController(cc) with AuthorisedAgentSupport {

  def optin(arn: Arn, lang: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withMatchedArn(arn, authorisedAgent) {
        optinService.optin(authorisedAgent.arn, authorisedAgent.agentUser, lang) flatMap {
          case None =>
            logger.info(s"Already has $OptedIn")
            Future.successful(Conflict)
          case Some(optinRequestStatus) =>
            logger.info(s"Opted In, status: $optinRequestStatus")
            Future.successful(Created)
        }
      }
    } transformWith failureHandler
  }

  def optout(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent() { authorisedAgent =>
      withMatchedArn(arn, authorisedAgent) {
        optinService.optout(authorisedAgent.arn, authorisedAgent.agentUser) flatMap {
          case None =>
            logger.info(s"Already has $OptedOut")
            Future.successful(Conflict)
          case Some(optoutRequestStatus) =>
            logger.info(s"Opted Out, status: $optoutRequestStatus")
            Future.successful(Created)
        }
      }
    } transformWith failureHandler
  }

  def optinStatus(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { _ =>
      optinService
        .optinStatus(arn)
        .map(_.map(_.value))
        .map {
          case None              => NotFound
          case Some(optinStatus) => Ok(JsString(optinStatus))
        }
    } transformWith failureHandler
  }

  def optinRecordExists(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { _ =>
      optinService
        .optinRecordExists(arn)
        .map {
          case true  => NoContent
          case false => NotFound
        }
    } transformWith failureHandler
  }

  private def withMatchedArn(providedArn: Arn, authorisedAgent: AuthorisedAgent)(
    body: => Future[Result]
  ): Future[Result] =
    if (providedArn == authorisedAgent.arn) {
      body
    } else {
      logger.info("Provided ARN did not match with that identified by auth")
      Future.successful(BadRequest)
    }

  private def failureHandler(triedResult: Try[Result]): Future[Result] = triedResult match {
    case Success(result) =>
      Future.successful(result)
    case Failure(ex) =>
      logger.info(s"Returning $InternalServerError: ${ex.getMessage}")
      Future.successful(InternalServerError)
  }

}
