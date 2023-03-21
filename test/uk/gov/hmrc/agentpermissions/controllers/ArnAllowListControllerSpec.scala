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

import org.scalamock.handlers.{CallHandler0, CallHandler4}
import play.api.http.Status.{FORBIDDEN, OK}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.repository.{RecordInserted, UpsertType}
import uk.gov.hmrc.agentpermissions.service.BetaInviteService
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ArnAllowListControllerSpec extends BaseSpec {

  "Is ARN Allowed" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result: Future[Result] = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "authorised agent is identified by auth" should {
      s"return $OK" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result: Future[Result] = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe OK
      }
    }
  }

  "hideBetaInviteCheck" when {
    val req = FakeRequest("GET", routes.ArnAllowListController.hideBetaInviteCheck.url)
    "allowlist enabled" should {
      s"return $OK if agent is on allowlist" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockAppConfigCheckArnAllowList(true)
        mockAppConfigAllowedArns(Seq(arn.value))

        val result: Future[Result] = controller.hideBetaInviteCheck()(req)
        status(result) shouldBe OK
      }

      s"return $OK if agent is not on allowlist but hideBetaInvite is true" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arnNotOnAllowlist, user)))
        mockAppConfigCheckArnAllowList(true)
        mockAppConfigAllowedArns(Seq.empty)
        mockHideBetaInviteCheck(true)

        val result: Future[Result] = controller.hideBetaInviteCheck()(req)
        status(result) shouldBe OK
      }

      s"return $NOT_FOUND if agent is not on allowlist and hideBetaInvite is false" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arnNotOnAllowlist, user)))
        mockAppConfigCheckArnAllowList(true)
        mockAppConfigAllowedArns(Seq.empty)
        mockHideBetaInviteCheck(false)

        val result: Future[Result] = controller.hideBetaInviteCheck()(req)
        status(result) shouldBe NOT_FOUND
      }
    }

    "allowlist disabled" should {
      s"return $OK if hideBetaInvite is true" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arnNotOnAllowlist, user)))
        mockAppConfigCheckArnAllowList(false)
        mockHideBetaInviteCheck(true)

        val result: Future[Result] = controller.hideBetaInviteCheck()(req)
        status(result) shouldBe OK
      }

      s"return $NOT_FOUND if hideBetaInvite is false" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arnNotOnAllowlist, user)))
        mockAppConfigCheckArnAllowList(false)
        mockHideBetaInviteCheck(false)

        val result: Future[Result] = controller.hideBetaInviteCheck()(req)
        status(result) shouldBe NOT_FOUND
      }
    }
  }

  "hideBetaInvite" should {
    val postReq = FakeRequest("POST", routes.ArnAllowListController.hideBetaInvite.url)
    s"return $CREATED for BetaInviteRecord" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockHideBetaInviteCreated(user.id)
      val result: Future[Result] = controller.hideBetaInvite()(postReq)
      status(result) shouldBe CREATED
    }

    s"return $CONFLICT if BetaInviteRecord already exists" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockHideBetaInviteConflict()

      val result2: Future[Result] = controller.hideBetaInvite()(postReq)
      status(result2) shouldBe CONFLICT
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN0762398")
    val arnNotOnAllowlist: Arn = Arn("BARN0762391")
    val user: AgentUser = AgentUser("userId", "userName")
    val allowlist: Seq[String] = Seq("KARN0762398")

    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val appConfig: AppConfig = mock[AppConfig]
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    implicit val mockBetaInviteService: BetaInviteService = mock[BetaInviteService]
    implicit val mockAuthAction: AuthAction = mock[AuthAction]

    val controller = new ArnAllowListController()

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler4[Boolean, Boolean, ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent(_: Boolean, _: Boolean)(_: ExecutionContext, _: Request[_]))
        .expects(*, *, *, *)
        .returning(Future.successful(maybeAuthorisedAgent))

    def mockHideBetaInviteCheck(
      hideBetaInviteCheck: Boolean
    ): CallHandler4[Arn, AgentUser, ExecutionContext, HeaderCarrier, Future[Boolean]] =
      (mockBetaInviteService
        .hideBetaInviteCheck(_: Arn, _: AgentUser)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arnNotOnAllowlist, user, *, *)
        .returning(Future.successful(hideBetaInviteCheck))

    def mockHideBetaInviteCreated(
      id: String
    ): CallHandler4[Arn, AgentUser, ExecutionContext, HeaderCarrier, Future[Option[UpsertType]]] =
      (mockBetaInviteService
        .hideBetaInvite(_: Arn, _: AgentUser)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, user, *, *)
        .returning(Future.successful(Some(RecordInserted(id))))

    def mockHideBetaInviteConflict()
      : CallHandler4[Arn, AgentUser, ExecutionContext, HeaderCarrier, Future[Option[UpsertType]]] =
      (mockBetaInviteService
        .hideBetaInvite(_: Arn, _: AgentUser)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, user, *, *)
        .returning(Future.successful(None))

    def mockAppConfigCheckArnAllowList(toCheckArnAllowList: Boolean): CallHandler0[Boolean] =
      (() => appConfig.checkArnAllowList)
        .expects()
        .returning(toCheckArnAllowList)

    def mockAppConfigAllowedArns(allowedArns: Seq[String]): CallHandler0[Seq[String]] =
      (() => appConfig.allowedArns)
        .expects()
        .returning(allowedArns)
  }

  def buildRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", routes.ArnAllowListController.isArnAllowed.url)

}
