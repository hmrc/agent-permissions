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

import play.api.mvc.Request
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, credentialRole, credentials, name}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthAction @Inject() (val authConnector: AuthConnector, val env: Environment, val config: Configuration)
    extends AuthRedirects with AuthorisedFunctions with Logging {

  private val agentEnrolment = "HMRC-AS-AGENT"
  private val agentReferenceNumberIdentifier = "AgentReferenceNumber"

  def getAuthorisedAgent()(implicit
    ec: ExecutionContext,
    request: Request[_]
  ): Future[Option[(Arn, AgentUser)]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(AuthProviders(GovernmentGateway) and Enrolment(agentEnrolment))
      .retrieve(allEnrolments and credentialRole and name and credentials) {
        case enrols ~ credRole ~ name ~ credentials =>
          getArnAndAgentUser(enrols, name, credentials) match {
            case Some((arn, user)) =>
              credRole match {
                case Some(User) | Some(Admin) => Future.successful(Option((arn, user)))
                case _ =>
                  logger.warn("Invalid credential role")
                  Future.successful(None)
              }
            case None =>
              logger.warn("No " + agentReferenceNumberIdentifier + " in enrolment")
              Future.successful(None)
          }
      }
  }

  private def getArnAndAgentUser(
    enrolments: Enrolments,
    maybeName: Option[Name],
    maybeCredentials: Option[Credentials]
  ): Option[(Arn, AgentUser)] =
    for {
      enrolment   <- enrolments.getEnrolment(agentEnrolment)
      identifier  <- enrolment.getIdentifier(agentReferenceNumberIdentifier)
      credentials <- maybeCredentials
      name        <- maybeName
    } yield (
      Arn(identifier.value),
      AgentUser(credentials.providerId, name.name.getOrElse("") + " " + name.lastName.getOrElse(""))
    )

}
