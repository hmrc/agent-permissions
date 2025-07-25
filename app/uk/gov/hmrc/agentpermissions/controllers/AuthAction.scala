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

import play.api.mvc.Request
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, credentialRole, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AuthAction @Inject() (
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration
)(implicit appConfig: AppConfig)
    extends AuthorisedFunctions with Logging {

  private val agentEnrolment = "HMRC-AS-AGENT"
  private val agentReferenceNumberIdentifier = "AgentReferenceNumber"

  def getAuthorisedAgent(allowStandardUser: Boolean = false, allowlistEnabled: Boolean = true)(implicit
    ec: ExecutionContext,
    request: Request[_]
  ): Future[Option[AuthorisedAgent]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(AuthProviders(GovernmentGateway) and Enrolment(agentEnrolment))
      .retrieve(allEnrolments and credentialRole and credentials) { case allEnrolments ~ credentialRole ~ credentials =>
        getArnAndAgentUser(allEnrolments, credentials) match {
          case Some(authorisedAgent) =>
            if (
              credentialRole.contains(User) | credentialRole
                .contains(Admin) | (credentialRole.contains(Assistant) & allowStandardUser)
            ) {
              if (appConfig.checkArnAllowList & allowlistEnabled) {
                if (appConfig.allowedArns.contains(authorisedAgent.arn.value)) {
                  Future successful Option(authorisedAgent)
                } else {
                  Future successful None
                }
              } else {
                Future successful Option(authorisedAgent)
              }
            } else {
              logger.warn(s"Invalid credential role $credentialRole")
              Future.successful(None)
            }
          case None =>
            logger.warn("No " + agentReferenceNumberIdentifier + " in enrolment")
            Future.successful(None)
        }
      } transformWith failureHandler
  }

  private def getArnAndAgentUser(
    enrolments: Enrolments,
    maybeCredentials: Option[Credentials]
  ): Option[AuthorisedAgent] =
    for {
      enrolment   <- enrolments.getEnrolment(agentEnrolment)
      identifier  <- enrolment.getIdentifier(agentReferenceNumberIdentifier)
      credentials <- maybeCredentials
    } yield AuthorisedAgent(
      Arn(identifier.value),
      AgentUser(
        credentials.providerId,
        ""
      ) // TODO: Setting name field as blank until AgentUser is removed completely (See APB-8252)
    )

  private def failureHandler(triedResult: Try[Option[AuthorisedAgent]]): Future[Option[AuthorisedAgent]] =
    triedResult match {
      case Success(maybeAuthorisedAgent) =>
        Future.successful(maybeAuthorisedAgent)
      case Failure(ex) =>
        logger.warn(s"Error authorising: ${ex.getMessage}")
        Future.successful(None)
    }

}

case class AuthorisedAgent(arn: Arn, agentUser: AgentUser)
