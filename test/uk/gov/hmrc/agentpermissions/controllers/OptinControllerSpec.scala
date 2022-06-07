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

import org.scalamock.handlers.{CallHandler2, CallHandler3}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service._

import scala.concurrent.{ExecutionContext, Future}

class OptinControllerSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val optinService: OptinService = mock[OptinService]
    val authAction: AuthAction = mock[AuthAction]
    implicit val appConfig: AppConfig = mock[AppConfig]
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest

    val controller = new OptinController(optinService, authAction)

    def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler2[ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] = (authAction
      .getAuthorisedAgent()(_: ExecutionContext, _: Request[_]))
      .expects(*, *)
      .returning(Future.successful(maybeAuthorisedAgent))

    def mockOptinServiceOptin(
      maybeOptinRequestStatus: Option[OptinRequestStatus]
    ): CallHandler3[Arn, AgentUser, ExecutionContext, Future[Option[OptinRequestStatus]]] = (optinService
      .optin(_: Arn, _: AgentUser)(_: ExecutionContext))
      .expects(arn, user, *)
      .returning(Future.successful(maybeOptinRequestStatus))

    def mockOptinServiceOptout(
      maybeOptoutRequestStatus: Option[OptoutRequestStatus]
    ): CallHandler3[Arn, AgentUser, ExecutionContext, Future[Option[OptoutRequestStatus]]] = (optinService
      .optout(_: Arn, _: AgentUser)(_: ExecutionContext))
      .expects(arn, user, *)
      .returning(Future.successful(maybeOptoutRequestStatus))

  }

  "Call to opt-in" when {

    "authorised agent is not identified by auth" should {

      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.optin(arn)(request)
        status(result) shouldBe FORBIDDEN
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptin(Some(OptinCreated))

        val result = controller.optin(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptin(None)

        val result = controller.optin(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(Arn("NARN0101010"), user)))

        val result = controller.optin(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "Call to opt-out" when {

    "authorised agent is not identified by auth" should {

      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.optout(arn)(request)
        status(result) shouldBe FORBIDDEN
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptout(Some(OptoutCreated))

        val result = controller.optout(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptout(None)

        val result = controller.optout(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(Arn("NARN0101010"), user)))

        val result = controller.optout(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
