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

package uk.gov.hmrc.agentpermissions.connectors

import com.google.inject.AbstractModule
import play.api.http.Status.{FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, NO_CONTENT, OK, UNAUTHORIZED}
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseIntegrationSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class UserClientDetailsConnectorIntegrationSpec extends BaseIntegrationSpec {

  val arn = Arn("TARN0000001")

  val httpClient: HttpClient = mock[HttpClient]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit =
      bind(classOf[HttpClient]).toInstance(httpClient)
  }

  trait TestScope {
    lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    lazy val userClientDetailsConnector: UserClientDetailsConnector =
      app.injector.instanceOf[UserClientDetailsConnector]

    def mockHttpGet[A](url: String, response: A): Unit =
      (httpClient
        .GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])(
          _: HttpReads[A],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(url, *, *, *, *, *)
        .returning(Future.successful(response))
  }

  "agentSize" when {

    s"http response has $OK status code" should {
      "return the client count" in new TestScope {
        val clientCount = 10
        mockHttpGet(
          s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size",
          HttpResponse(OK, Json.obj("client-count" -> clientCount).toString)
        )

        userClientDetailsConnector.agentSize(arn).futureValue shouldBe Some(clientCount)
      }
    }

    "http response has non-200 status codes" should {
      "return nothing" in new TestScope {
        Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
          mockHttpGet(
            s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.agentSize(arn).futureValue shouldBe None
        }
      }
    }
  }

  "userCheck" when {

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockHttpGet(
          s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
          HttpResponse(NO_CONTENT, "")
        )

        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(false)
      }
    }

    s"http response has $FORBIDDEN status code" should {
      "return true" in new TestScope {
        mockHttpGet(
          s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
          HttpResponse(FORBIDDEN, "")
        )

        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(true)
      }
    }

    "http response has non-200 status codes" should {
      "return nothing" in new TestScope {
        Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
          mockHttpGet(
            s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe None
        }
      }
    }
  }

  "outstandingWorkItemsExist" when {

    s"http response has $OK status code" should {
      "return true" in new TestScope {
        mockHttpGet(
          s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
          HttpResponse(OK, "")
        )

        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(true)
      }
    }

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockHttpGet(
          s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
          HttpResponse(NO_CONTENT, "")
        )

        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(false)
      }
    }

    "http response has non-200 status codes" should {
      "return nothing" in new TestScope {
        Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
          mockHttpGet(
            s"${appConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe None
        }
      }
    }
  }

}
