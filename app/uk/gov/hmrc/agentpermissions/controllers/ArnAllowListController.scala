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

import play.api.mvc._
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service.BetaInviteService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ArnAllowListController @Inject() (implicit
  appConfig: AppConfig,
  betaInviteService: BetaInviteService,
  authAction: AuthAction,
  cc: ControllerComponents,
  ec: ExecutionContext
) extends BackendController(cc) with AuthorisedAgentSupport {

  def isArnAllowed: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true) { _ =>
      Future successful Ok
    }
  }

  def hideBetaInviteCheck: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true, allowlistEnabled = false) { authorisedAgent =>
      if (appConfig.checkArnAllowList) {
        if (appConfig.allowedArns.contains(authorisedAgent.arn.value)) {
          Future successful Ok
        } else {
          for {
            hideBetaInvite <- betaInviteService.hideBetaInviteCheck(authorisedAgent.arn, authorisedAgent.agentUser)
          } yield
            if (hideBetaInvite) {
              Ok
            } else {
              NotFound
            }
        }
      } else {
        for {
          hideBetaInvite <- betaInviteService.hideBetaInviteCheck(authorisedAgent.arn, authorisedAgent.agentUser)
        } yield
          if (hideBetaInvite) {
            Ok
          } else {
            NotFound
          }
      }
    }
  }

  def hideBetaInvite: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent(allowStandardUser = true, allowlistEnabled = false) { authorisedAgent =>
      betaInviteService
        .hideBetaInvite(authorisedAgent.arn, authorisedAgent.agentUser)
        .map(x =>
          if (x.isDefined) {
            Created
          } else {
            Conflict
          }
        )
    }
  }

}
