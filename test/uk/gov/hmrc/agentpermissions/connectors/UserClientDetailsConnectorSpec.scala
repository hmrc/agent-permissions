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

package uk.gov.hmrc.agentpermissions.connectors

import com.codahale.metrics.{MetricRegistry, NoopMetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.scalamock.handlers.CallHandler0
import play.api.http.Status._
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, AssignedClient, Client, GroupDelegatedEnrolments, UserEnrolmentAssignments}
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

  "clientCountByTaxService" when {

    s"http response has $OK status code" should {
      "return the client count by tax service" in new TestScope {
        val clientCount: Map[String, Int] = Map("HMRC-MTD-VAT" -> 7)
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/tax-service-client-count",
          HttpResponse(OK, Json.toJson(clientCount).toString)
        )

        userClientDetailsConnector.clientCountByTaxService(arn).futureValue shouldBe Some(clientCount)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/tax-service-client-count",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.clientCountByTaxService(arn).futureValue shouldBe None
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

  "outstandingAssignmentsWorkItemsExist" when {

    s"http response has $OK status code" should {
      "return true" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist",
          HttpResponse(OK, "")
        )

        userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe Some(true)
      }
    }

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist",
          HttpResponse(NO_CONTENT, "")
        )

        userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe Some(false)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe None
        }
      }
    }
  }

  "getClients" when {

    s"http response has $ACCEPTED status code and to send email" should {
      "return some value" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list?sendEmail=true",
          HttpResponse(ACCEPTED, "[]")
        )

        userClientDetailsConnector.getClients(arn, sendEmail = true).futureValue shouldBe Some(Seq.empty)
      }
    }

    s"http response has $ACCEPTED status code and to send email and lang is provided" should {
      "return some value" in new TestScope {
        val lang = "cy"
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list?sendEmail=true&lang=$lang",
          HttpResponse(ACCEPTED, "[]")
        )

        userClientDetailsConnector.getClients(arn, sendEmail = true, lang = Some(lang)).futureValue shouldBe Some(
          Seq.empty
        )
      }
    }

    s"http response has $ACCEPTED status code" should {
      "return some value" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list",
          HttpResponse(ACCEPTED, "[]")
        )

        userClientDetailsConnector.getClients(arn).futureValue shouldBe Some(Seq.empty)
      }
    }

    s"http response has $OK status code" should {
      "return some value" in new TestScope {
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

  "getClientListStatus" when {

    s"http response has $ACCEPTED status code" should {
      s"return $ACCEPTED" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status",
          HttpResponse(ACCEPTED, "[]")
        )

        userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe Some(ACCEPTED)
      }
    }

    s"http response has $OK status code" should {
      s"return $OK" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status",
          HttpResponse(OK, "[]")
        )

        userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe Some(OK)
      }
    }

    "http response has 4xx status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe None
        }
      }
    }

    "http response has 5xx status codes" should {
      Seq(INTERNAL_SERVER_ERROR, BAD_GATEWAY).foreach { statusCode =>
        s"throw upstream exception nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status",
            HttpResponse(statusCode, "")
          )

          val eventualMaybeClientListStatus: Future[Option[Int]] = userClientDetailsConnector.getClientListStatus(arn)
          whenReady(eventualMaybeClientListStatus.failed) { ex =>
            ex shouldBe a[UpstreamErrorResponse]
          }
        }
      }
    }
  }

  "push calculated assignments" when {

    s"http response has $ACCEPTED status code" should {
      s"return $AssignmentsPushed" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpPost(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/user-enrolment-assignments",
          HttpResponse(ACCEPTED, "")
        )

        userClientDetailsConnector
          .pushAssignments(UserEnrolmentAssignments(Set.empty, Set.empty, arn))
          .futureValue shouldBe AssignmentsPushed
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpPost(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/user-enrolment-assignments",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector
            .pushAssignments(UserEnrolmentAssignments(Set.empty, Set.empty, arn))
            .futureValue shouldBe AssignmentsNotPushed
        }
      }
    }
  }

  "getClientsWithAssignedUsers" when {

    s"http response has $OK status code" should {
      "return some value" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGet(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/clients-assigned-users",
          HttpResponse(OK, Json.obj("clients" -> Seq.empty[AssignedClient]).toString)
        )

        userClientDetailsConnector.getClientsWithAssignedUsers(arn).futureValue shouldBe Some(
          GroupDelegatedEnrolments(Seq.empty)
        )
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGet(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/clients-assigned-users",
            HttpResponse(statusCode, "")
          )

          userClientDetailsConnector.getClientsWithAssignedUsers(arn).futureValue shouldBe None
        }
      }
    }
  }

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

    def mockHttpPost[I, A](url: String, response: A): Unit =
      (mockHttpClient
        .POST[I, A](_: String, _: I, _: Seq[(String, String)])(
          _: Writes[I],
          _: HttpReads[A],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(url, *, *, *, *, *, *)
        .returning(Future.successful(response))
  }

}
