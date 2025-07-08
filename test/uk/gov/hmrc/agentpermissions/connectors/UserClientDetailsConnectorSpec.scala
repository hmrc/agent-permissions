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
import izumi.reflect.Tag
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.handlers.{CallHandler0, CallHandler1, CallHandler2, CallHandler4}
import play.api.http.Status._
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.{BodyWritable, DefaultBodyWritables, WSRequest}
import uk.gov.hmrc.agentmtdidentifiers.model.{PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.UserEnrolmentAssignments
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder, StreamHttpReads}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class UserClientDetailsConnectorSpec extends BaseSpec {

  val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
  val mockMetrics: Metrics = mock[Metrics]
  val noopMetricRegistry = new NoopMetricRegistry

  implicit val mockAppConfig: AppConfig = mock[AppConfig]

  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Option(Authorization("Bearer XYZ")))
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "agentSize" when {

    s"http response has $OK status code" should {
      "return the client count" in new TestScope {
        val clientCount = 10
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry

        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size"
          )
        )
        mockRequestBuilderTransform
        mockRequestBuilderExecuteWithoutException(AgentClientSize(10))

        userClientDetailsConnector.agentSize(arn).futureValue shouldBe Some(clientCount)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/agent-size"
            )
          )
          mockRequestBuilderTransform
          mockRequestBuilderExecuteWithException(UpstreamErrorResponse("boo boo", statusCode))

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
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/tax-service-client-count"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(OK, Json.toJson(clientCount).toString))
        userClientDetailsConnector.clientCountByTaxService(arn).futureValue shouldBe Some(clientCount)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/tax-service-client-count"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
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
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(NO_CONTENT, ""))
        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(false)
      }
    }

    s"http response has $FORBIDDEN status code" should {
      "return true" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(FORBIDDEN, ""))
        userClientDetailsConnector.isSingleUserAgency(arn).futureValue shouldBe Some(true)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user-check"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
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
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(OK, ""))
        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(true)
      }
    }

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(NO_CONTENT, ""))
        userClientDetailsConnector.outstandingWorkItemsExist(arn).futureValue shouldBe Some(false)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/work-items-exist"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
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
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(OK, ""))
        userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe Some(true)
      }
    }

    s"http response has $NO_CONTENT status code" should {
      "return false" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(NO_CONTENT, ""))
        userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn).futureValue shouldBe Some(false)
      }
    }

    "http response has non-200 status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
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
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list?sendEmail=true"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(ACCEPTED, "[]"))
        userClientDetailsConnector.getClients(arn, sendEmail = true).futureValue shouldBe Some(Seq.empty)
      }
    }

    s"http response has $ACCEPTED status code and to send email and lang is provided" should {
      "return some value" in new TestScope {
        val lang = "cy"
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list?sendEmail=true&lang=$lang"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(ACCEPTED, "[]"))
        userClientDetailsConnector.getClients(arn, sendEmail = true, lang = Some(lang)).futureValue shouldBe Some(
          Seq.empty
        )
      }
    }

    s"http response has $ACCEPTED status code" should {
      "return some value" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(ACCEPTED, "[]"))
        userClientDetailsConnector.getClients(arn).futureValue shouldBe Some(Seq.empty)
      }
    }

    s"http response has $OK status code" should {
      "return some value" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(OK, "[]"))
        userClientDetailsConnector.getClients(arn).futureValue shouldBe Some(Seq.empty)
      }
    }

    "http response has 4xx status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
          userClientDetailsConnector.getClients(arn).futureValue shouldBe None
        }
      }
    }

    "http response has 5xx status codes" should {
      Seq(INTERNAL_SERVER_ERROR, BAD_GATEWAY).foreach { statusCode =>
        s"throw upstream exception nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
          val eventualMaybeClients: Future[Option[Seq[Client]]] = userClientDetailsConnector.getClients(arn)
          whenReady(eventualMaybeClients.failed) { ex =>
            ex shouldBe a[UpstreamErrorResponse]
          }
        }
      }
    }
  }

  "getPaginatedClientsList" should {

    "return a PaginatedList[Client] when status response is OK" in new TestScope {
      val SEARCH = "rob"
      val FILTER = "whatever"
      val PAGE = 1
      val clients = Seq(
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12345", friendlyName = "Bob"),
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12347", friendlyName = "Builder")
      )
      val meta = PaginationMetaData(
        lastPage = false,
        firstPage = true,
        totalSize = 2,
        totalPages = 1,
        pageSize = 20,
        currentPageNumber = 1,
        currentPageSize = 2
      )
      val paginatedList = PaginatedList[Client](pageContent = clients, paginationMetaData = meta)
      mockAppConfigAgentUserClientDetailsBaseUrl
      mockMetricsDefaultRegistry
      private val PAGE_SIZE = 5
      mockHttpGetV2(
        new URL(
          s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/clients?page=$PAGE&pageSize=$PAGE_SIZE&search=$SEARCH&filter=$FILTER"
        )
      )
      mockRequestBuilderExecuteWithoutException(HttpResponse(OK, Json.toJson(paginatedList).toString))

      // when
      val result: Future[PaginatedList[Client]] =
        userClientDetailsConnector.getPaginatedClients(arn)(PAGE, PAGE_SIZE, Some(SEARCH), Some(FILTER))

      // then
      result.futureValue shouldBe paginatedList
    }

  }

  "getClientListStatus" when {

    s"http response has $ACCEPTED status code" should {
      s"return $ACCEPTED" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(ACCEPTED, "[]"))
        userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe Some(ACCEPTED)
      }
    }

    s"http response has $OK status code" should {
      s"return $OK" in new TestScope {
        mockAppConfigAgentUserClientDetailsBaseUrl
        mockMetricsDefaultRegistry
        mockHttpGetV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status"
          )
        )
        mockRequestBuilderExecuteWithoutException(HttpResponse(OK, "[]"))
        userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe Some(OK)
      }
    }

    "http response has 4xx status codes" should {
      Seq(NOT_FOUND, UNAUTHORIZED).foreach { statusCode =>
        s"return nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
          userClientDetailsConnector.getClientListStatus(arn).futureValue shouldBe None
        }
      }
    }

    "http response has 5xx status codes" should {
      Seq(INTERNAL_SERVER_ERROR, BAD_GATEWAY).foreach { statusCode =>
        s"throw upstream exception nothing for $statusCode" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/client-list-status"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(statusCode, ""))
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
        mockHttpPostV2(
          new URL(
            s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/user-enrolment-assignments"
          )
        )
        mockRequestBuilderWithBody(Json.toJson(UserEnrolmentAssignments(Set.empty, Set.empty, arn)))

        mockRequestBuilderExecuteWithoutException(
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
          mockHttpPostV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/user-enrolment-assignments"
            )
          )

          mockRequestBuilderWithBody(Json.toJson(UserEnrolmentAssignments(Set.empty, Set.empty, arn)))

          mockRequestBuilderExecuteWithoutException(
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

    "getTeamMembers" when {

      s"http response has $OK status code" should {
        "return some value" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpGetV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/team-members"
            )
          )
          mockRequestBuilderExecuteWithoutException(HttpResponse(OK, "[]"))
          userClientDetailsConnector.getTeamMembers(arn).futureValue shouldBe Seq.empty
        }
      }

      "http response has non-200 status codes" should {
        Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
          s"fail for $statusCode" in new TestScope {
            mockAppConfigAgentUserClientDetailsBaseUrl
            mockMetricsDefaultRegistry
            mockHttpGetV2(
              new URL(
                s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/team-members"
              )
            )
            mockRequestBuilderExecuteWithException(UpstreamErrorResponse("", statusCode))
            userClientDetailsConnector.getTeamMembers(arn).failed.futureValue shouldBe a[UpstreamErrorResponse]
          }
        }
      }

    }

    "syncTeamMember" when {
      val userId = "myUser"

      s"http response has $OK status code" should {
        "return false" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpPostV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user/$userId/ensure-assignments"
            )
          )
          mockRequestBuilderWithBody(JsArray.empty)
          mockRequestBuilderExecuteWithoutException(HttpResponse(OK, ""))
          userClientDetailsConnector.syncTeamMember(arn, userId, Seq.empty).futureValue shouldBe false
        }
      }

      s"http response has $ACCEPTED status code" should {
        "return true" in new TestScope {
          mockAppConfigAgentUserClientDetailsBaseUrl
          mockMetricsDefaultRegistry
          mockHttpPostV2(
            new URL(
              s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user/$userId/ensure-assignments"
            )
          )
          mockRequestBuilderWithBody(JsArray.empty)
          mockRequestBuilderExecuteWithoutException(HttpResponse(ACCEPTED, ""))
          userClientDetailsConnector.syncTeamMember(arn, userId, Seq.empty).futureValue shouldBe true
        }
      }

      "http response has non-successful status codes" should {
        Seq(NOT_FOUND, UNAUTHORIZED, INTERNAL_SERVER_ERROR).foreach { statusCode =>
          s"fail for $statusCode" in new TestScope {
            mockAppConfigAgentUserClientDetailsBaseUrl
            mockMetricsDefaultRegistry
            mockHttpPostV2(
              new URL(
                s"${mockAppConfig.agentUserClientDetailsBaseUrl}/agent-user-client-details/arn/${arn.value}/user/$userId/ensure-assignments"
              )
            )
            mockRequestBuilderWithBody(JsArray.empty)
            mockRequestBuilderExecuteWithException(UpstreamErrorResponse("", statusCode))
            userClientDetailsConnector
              .syncTeamMember(arn, userId, Seq.empty)
              .failed
              .futureValue shouldBe a[UpstreamErrorResponse]
          }
        }
      }

    }

  }

  trait TestScope extends DefaultBodyWritables {
    lazy val userClientDetailsConnector: UserClientDetailsConnector =
      new UserClientDetailsConnectorImpl(mockHttpClientV2, executionContext, mockMetrics)

    implicit val materializer: Materializer = Materializer(ActorSystem())

    def mockAppConfigAgentUserClientDetailsBaseUrl: CallHandler0[String] =
      (() => mockAppConfig.agentUserClientDetailsBaseUrl)
        .expects()
        .returning("http://someBaseUrl")
        .noMoreThanTwice()

    def mockMetricsDefaultRegistry: CallHandler0[MetricRegistry] =
      (() => mockMetrics.defaultRegistry)
        .expects()
        .returning(noopMetricRegistry)

    def mockHttpGetV2[A](url: URL): CallHandler2[URL, HeaderCarrier, RequestBuilder] =
      (mockHttpClientV2
        .get(_: URL)(_: HeaderCarrier))
        .expects(url, *)
        .returning(mockRequestBuilder)

    def mockRequestBuilderTransform: CallHandler1[WSRequest => WSRequest, RequestBuilder] =
      (mockRequestBuilder
        .transform(_: WSRequest => WSRequest))
        .expects(*)
        .returning(mockRequestBuilder)
    def mockRequestBuilderWithBody[JsValue](
      body: JsValue
    ): CallHandler4[JsValue, BodyWritable[JsValue], Tag[JsValue], ExecutionContext, RequestBuilder] =
      (mockRequestBuilder
        .withBody(_: JsValue)(_: BodyWritable[JsValue], _: Tag[JsValue], _: ExecutionContext))
        .expects(body, *, *, *)
        .returning(mockRequestBuilder)

    def mockRequestBuilderStream[A: StreamHttpReads](
      stream: A
    ): CallHandler2[StreamHttpReads[A], ExecutionContext, Future[A]] =
      (mockRequestBuilder
        .stream(_: StreamHttpReads[A], _: ExecutionContext))
        .expects(*, *)
        .returning(Future successful stream)

    def mockRequestBuilderStreamFailed[A](
      ex: Exception
    ): CallHandler2[StreamHttpReads[A], ExecutionContext, Future[A]] =
      (mockRequestBuilder
        .stream(_: StreamHttpReads[A], _: ExecutionContext))
        .expects(*, *)
        .returning(Future failed ex)

    def mockRequestBuilderExecuteWithoutException[A](
      value: A
    ): CallHandler2[HttpReads[A], ExecutionContext, Future[A]] =
      (mockRequestBuilder
        .execute(_: HttpReads[A], _: ExecutionContext))
        .expects(*, *)
        .returning(Future successful value)

    def mockRequestBuilderExecuteWithException[A](
      ex: Exception
    ): CallHandler2[HttpReads[A], ExecutionContext, Future[A]] =
      (mockRequestBuilder
        .execute(_: HttpReads[A], _: ExecutionContext))
        .expects(*, *)
        .returning(Future failed ex)

    def mockHttpPostV2[A](url: URL): CallHandler2[URL, HeaderCarrier, RequestBuilder] =
      (mockHttpClientV2
        .post(_: URL)(_: HeaderCarrier))
        .expects(url, *)
        .returning(mockRequestBuilder)
  }

}
