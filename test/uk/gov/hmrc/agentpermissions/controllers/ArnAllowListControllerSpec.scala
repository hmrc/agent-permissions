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

import org.scalamock.handlers.CallHandler2
import play.api.http.Status.{FORBIDDEN, OK}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ArnAllowListControllerSpec extends BaseSpec {

  "Is ARN Allowed" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "authorised agent is identified by auth" should {
      s"return $OK" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe OK
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN0762398")
    val user: AgentUser = AgentUser("userId", "userName")

    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    implicit val mockAuthAction: AuthAction = mock[AuthAction]

    val controller = new ArnAllowListController(mockAuthAction)

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler2[ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent()(_: ExecutionContext, _: Request[_]))
        .expects(*, *)
        .returning(Future.successful(maybeAuthorisedAgent))
  }

  def buildRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", routes.ArnAllowListController.isArnAllowed.url)

}
