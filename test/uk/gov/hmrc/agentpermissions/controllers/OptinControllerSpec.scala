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

import org.scalamock.handlers.{CallHandler2, CallHandler3, CallHandler4, CallHandler5}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service._
import uk.gov.hmrc.http.HeaderCarrier

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

    def mockOptinServiceOptinWithoutException(
      maybeOptinRequestStatus: Option[OptinRequestStatus]
    ): CallHandler5[Arn, AgentUser, Option[String], ExecutionContext, HeaderCarrier, Future[
      Option[OptinRequestStatus]
    ]] = (optinService
      .optin(_: Arn, _: AgentUser, _: Option[String])(_: ExecutionContext, _: HeaderCarrier))
      .expects(arn, user, *, *, *)
      .returning(Future.successful(maybeOptinRequestStatus))

    def mockOptinServiceOptinWithException(
      ex: Exception
    ): CallHandler5[Arn, AgentUser, Option[String], ExecutionContext, HeaderCarrier, Future[
      Option[OptinRequestStatus]
    ]] =
      (optinService
        .optin(_: Arn, _: AgentUser, _: Option[String])(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, user, *, *, *)
        .returning(Future failed ex)

    def mockOptinServiceOptoutWithoutException(
      maybeOptoutRequestStatus: Option[OptoutRequestStatus]
    ): CallHandler4[Arn, AgentUser, ExecutionContext, HeaderCarrier, Future[Option[OptoutRequestStatus]]] =
      (optinService
        .optout(_: Arn, _: AgentUser)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, user, *, *)
        .returning(Future.successful(maybeOptoutRequestStatus))

    def mockOptinServiceOptoutWithException(
      ex: Exception
    ): CallHandler4[Arn, AgentUser, ExecutionContext, HeaderCarrier, Future[Option[OptoutRequestStatus]]] =
      (optinService
        .optout(_: Arn, _: AgentUser)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, user, *, *)
        .returning(Future failed ex)

    def mockOptinServiceOptinStatusWithoutException(
      maybeOptinStatis: Option[OptinStatus]
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[OptinStatus]]] =
      (optinService
        .optinStatus(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful maybeOptinStatis)

    def mockOptinServiceOptinStatusWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[OptinStatus]]] =
      (optinService
        .optinStatus(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)

    def mockOptinRecordExistsWithoutException(
      optinRecordExists: Boolean
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Boolean]] =
      (optinService
        .optinRecordExists(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future successful optinRecordExists)

    def mockOptinRecordExistsWithException(
      ex: Exception
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Boolean]] =
      (optinService
        .optinRecordExists(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *)
        .returning(Future failed ex)
  }

  "Call to opt-in" when {

    "authorised agent is not identified by auth" should {

      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.optin(arn)(request)
        status(result) shouldBe FORBIDDEN
      }
    }

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(Arn("NARN0101010"), user)))

        val result = controller.optin(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptinWithoutException(None)

        val result = controller.optin(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptinWithoutException(Some(OptinCreated))

        val result = controller.optin(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service throws an exception" should {

      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptinWithException(new RuntimeException("boo boo"))

        val result = controller.optin(arn)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
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

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(Arn("NARN0101010"), user)))

        val result = controller.optout(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptoutWithoutException(None)

        val result = controller.optout(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptoutWithoutException(Some(OptoutCreated))

        val result = controller.optout(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service throws an exception" should {

      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockOptinServiceOptoutWithException(new RuntimeException("boo boo"))

        val result = controller.optout(arn)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "Call to optin status" when {

    "optin service does not return an optin status record" should {
      s"return $NOT_FOUND" in new TestScope {
        mockOptinServiceOptinStatusWithoutException(None)

        val result = controller.optinStatus(arn)(request)
        status(result) shouldBe NOT_FOUND
      }
    }

    "optin service does return an optin status record" should {
      s"return $OK" in new TestScope {
        mockOptinServiceOptinStatusWithoutException(Some(OptedOutEligible))

        val result = controller.optinStatus(arn)(request)
        status(result) shouldBe OK
        contentAsString(result) shouldBe """"Opted-Out_ELIGIBLE""""
      }
    }

    "optin service throws exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockOptinServiceOptinStatusWithException(new RuntimeException("boo boo"))

        val result = controller.optinStatus(arn)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "Call to optin record exists" when {

    "optin record exists" should {
      s"return $NO_CONTENT" in new TestScope {
        mockOptinRecordExistsWithoutException(true)
        val result = controller.optinRecordExists(arn)(request)
        status(result) shouldBe NO_CONTENT
      }
    }

    "optin record does not exist" should {
      s"return $NOT_FOUND" in new TestScope {
        mockOptinRecordExistsWithoutException(false)
        val result = controller.optinRecordExists(arn)(request)
        status(result) shouldBe NOT_FOUND
      }
    }

    "optin service throws exception" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        mockOptinRecordExistsWithException(new RuntimeException("boo boo"))

        val result = controller.optinRecordExists(arn)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }

}
