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

package uk.gov.hmrc.agentpermissions

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, CredentialRole, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthorisationStub extends MockFactory {

  type GrantAccess = Enrolments ~ Option[CredentialRole] ~ Option[Name] ~ Option[Credentials]

  def stubAuthResponseWithoutException(response: GrantAccess)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def stubAuthResponseWithException(exceptionToReturn: Exception)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed exceptionToReturn)
}
