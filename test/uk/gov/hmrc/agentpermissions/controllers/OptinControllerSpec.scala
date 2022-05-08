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

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.repository.RecordInserted
import uk.gov.hmrc.agentpermissions.service.OptinService
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class OptinControllerSpec extends BaseSpec {

  def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")

    val optinService: OptinService = mock[OptinService]
    val authAction: AuthAction = mock[AuthAction]

    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val appConfig: AppConfig = new AppConfig(
      new ServicesConfig(
        GuiceApplicationBuilder()
          .configure(
            "microservice.services.auth.host" -> "someHost",
            "microservice.services.auth.port" -> "somePort"
          )
          .configuration
      )
    )

    val controller = new OptinController(optinService, authAction)
  }

  "Call to opt-in" when {

    "authorised agent is not identified by auth" should {

      s"return $FORBIDDEN" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent()).thenReturn(Future.successful(None))

        val result = controller.optin(arn)(request)
        status(result) shouldBe FORBIDDEN
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((arn, user))))
        when(optinService.optin(arn, user))
          .thenReturn(Future.successful(Some(RecordInserted)))

        val result = controller.optin(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((arn, user))))
        when(optinService.optin(arn, user))
          .thenReturn(Future.successful(None))

        val result = controller.optin(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((Arn("NARN0101010"), user))))

        val result = controller.optin(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "Call to opt-out" when {

    "authorised agent is not identified by auth" should {

      s"return $FORBIDDEN" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent()).thenReturn(Future.successful(None))

        val result = controller.optout(arn)(request)
        status(result) shouldBe FORBIDDEN
      }
    }

    s"optin service returns an optin record" should {

      s"return $CREATED" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((arn, user))))
        when(optinService.optout(arn, user))
          .thenReturn(Future.successful(Some(RecordInserted)))

        val result = controller.optout(arn)(request)
        status(result) shouldBe CREATED
      }
    }

    s"optin service does not return an optin record" should {

      s"return $CONFLICT" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((arn, user))))
        when(optinService.optout(arn, user))
          .thenReturn(Future.successful(None))

        val result = controller.optout(arn)(request)
        status(result) shouldBe CONFLICT
      }
    }

    s"auth returns a different arn than the provided one" should {

      s"return $BAD_REQUEST" in new TestScope {
        implicit val request = fakeRequest

        when(authAction.getAuthorisedAgent())
          .thenReturn(Future.successful(Some((Arn("NARN0101010"), user))))

        val result = controller.optout(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
