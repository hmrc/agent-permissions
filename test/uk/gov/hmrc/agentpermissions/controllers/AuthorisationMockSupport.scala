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

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.auth.core.{Assistant, AuthConnector, CredentialRole, Enrolment, EnrolmentIdentifier, Enrolments, User}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthorisationMockSupport extends BaseSpec with MockFactory {

  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(
    EnrolmentIdentifier(agentReferenceNumberIdentifier, "KARN1234567")
  )
  val agentEnrolment = "HMRC-AS-AGENT"

  val name: Name = Name(Some("Jane"), Some("Doe"))
  val emptyName: Name = Name(None, None)

  val ggCredentials: Credentials = Credentials("user1", "GovernmentGateway")

  val enrolments: Set[Enrolment] = Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))

  def buildAuthorisedResponse: GrantAccess =
    Enrolments(enrolments) and
      Some(User) and
      Some(name) and
      Some(ggCredentials)

  def buildUnauthorisedResponseHavingEmptyEnrolments: GrantAccess =
    Enrolments(Set.empty) and
      Some(User) and
      Some(name) and
      Some(ggCredentials)

  def buildUnauthorisedResponseHavingIncorrectCredentialRole: GrantAccess =
    Enrolments(enrolments) and
      None and
      Some(name) and
      Some(ggCredentials)

  def buildUnauthorisedResponseHavingIncorrectUsername: GrantAccess =
    Enrolments(enrolments) and
      Some(User) and
      None and
      Some(ggCredentials)

  def buildAuthorisedResponseHavingEmptyUsername: GrantAccess =
    Enrolments(enrolments) and
      Some(User) and
      Some(emptyName) and
      Some(ggCredentials)

  def buildAuthorisedResponseHavingAssistantCredentialRole: GrantAccess =
    Enrolments(enrolments) and
      Some(Assistant) and
      Some(name) and
      Some(ggCredentials)

  def buildUnauthorisedResponseHavingIncorrectCredentials: GrantAccess =
    Enrolments(enrolments) and
      Some(User) and
      Some(name) and
      None

  type GrantAccess = Enrolments ~ Option[CredentialRole] ~ Option[Name] ~ Option[Credentials]

  def mockAuthResponseWithoutException(response: GrantAccess)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def mockAuthResponseWithException(exceptionToReturn: Exception)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed exceptionToReturn)

}
