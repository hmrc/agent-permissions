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

import com.codahale.metrics.{MetricRegistry, NoopMetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.scalamock.handlers.CallHandler0
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class UserClientDetailsConnectorSpec extends BaseSpec {

  val arn: Arn = Arn("TARN0000001")

  val mockHttpClient: HttpClient = mock[HttpClient]
  val mockMetrics: Metrics = mock[Metrics]
  val noopMetricRegistry = new NoopMetricRegistry

  implicit val mockAppConfig: AppConfig = mock[AppConfig]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    lazy val userClientDetailsConnector: UserClientDetailsConnector =
      new UserClientDetailsConnectorImpl(mockHttpClient, mockMetrics)

    def mockAppConfigAgentUserClientDetailsBaseUrl: CallHandler0[String] =
      (mockAppConfig.agentUserClientDetailsBaseUrl _)
        .expects()
        .returning("http://someBaseUrl")
        .noMoreThanTwice

    def mockMetricsDefaultRegistry: CallHandler0[MetricRegistry] =
      (mockMetrics.defaultRegistry _)
        .expects()
        .returning(noopMetricRegistry)

    def mockHttpGet[A](url: String, response: A): Unit =
      (mockHttpClient
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
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size",
          HttpResponse(OK, Json.obj("client-count" -> clientCount).toString)
        )

        userClientDetailsConnector.agentSize(arn).futureValue shouldBe Some(clientCount)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size",
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
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
          HttpResponse(NO_CONTENT, "")
        )

        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(false)
      }
    }

    s"http response has $FORBIDDEN status code" should {
      "return true" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
          HttpResponse(FORBIDDEN, "")
        )

        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(true)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check",
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
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
          HttpResponse(OK, "")
        )

        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(true)
      }
    }

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
          HttpResponse(NO_CONTENT, "")
        )

        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(false)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe None
        }
      }
    }
  }

  "getClients" when {

    s"http response has $ACCEPTED status code" should {
      "return nothing" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list",
          HttpResponse(ACCEPTED, "")
        )

        userClientDetailsConnector.getClients(arn).futureValue shouldBe None
      }
    }

    s"http response has $OK status code" should {
      "return clients" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list",
          HttpResponse(OK, "[]")
        )

        userClientDetailsConnector.getClients(arn).futureValue shouldBe Some(Seq.empty)
      }
    }

    "http response has 4xx status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.getClients(arn).futureValue shouldBe None
        }
      }
    }

    "http response has 5xx status codes" should {
      Seq(INTERNAL_SERVER_ERROR, BAD_GATEWAY).foreach { statusCode =>
        s"throw upstream exception nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list",
            HttpResponse(statusCode, "")
          )

          val eventualMaybeClients: Future[Option[Seq[Client]]] = userClientDetailsConnector.getClients(arn)
          whenReady(eventualMaybeClients.failed) { ex =>
            ex shouldBe a[UpstreamErrorResponse]
          }
        }
      }
    }
  }

}
