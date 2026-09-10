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

import com.google.inject.ImplementedBy
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.*
import uk.gov.hmrc.agentpermissions.model.EacdAssignmentsPushStatus.{AssignmentsNotPushed, AssignmentsPushed}
import uk.gov.hmrc.agentpermissions.model.accessgroups.{Client, UserDetails}
import uk.gov.hmrc.agentpermissions.util.RequestAwareLogging
import uk.gov.hmrc.agentpermissions.util.RequestSupport.given
import uk.gov.hmrc.http.HttpErrorFunctions.is5xx
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpException, HttpResponse, NotFoundException, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@ImplementedBy(classOf[AgentUserClientDetailsConnectorImpl])
trait AgentUserClientDetailsConnector {
  def agentSize(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[Int]]

  def isSingleUserAgency(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[Boolean]]

  def outstandingWorkItemsExist(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[Boolean]]

  def outstandingAssignmentsWorkItemsExist(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Boolean]]
  def getClients(arn: Arn, sendEmail: Boolean = false, lang: Option[String] = None)(using
    RequestHeader,
    ExecutionContext
  ): Future[Option[Seq[Client]]]

  def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(using
    RequestHeader,
    ExecutionContext
  ): Future[PaginatedList[Client]]
  def pushAssignments(
    assignments: UserEnrolmentAssignments
  )(using RequestHeader, ExecutionContext): Future[EacdAssignmentsPushStatus]

  def getClientListStatus(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Int]]

  def clientCountByTaxService(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Map[String, Int]]]

  def getTeamMembers(arn: Arn)(using RequestHeader, ExecutionContext): Future[Seq[UserDetails]]

  def syncTeamMember(arn: Arn, userId: String, expectedAssignments: Seq[String] /* enrolment keys */ )(using
    RequestHeader,
    ExecutionContext
  ): Future[Boolean]
}

@Singleton
class AgentUserClientDetailsConnectorImpl @Inject() (httpV2: HttpClientV2)(using
  appConfig: AppConfig
) extends AgentUserClientDetailsConnector with RequestAwareLogging {

  import uk.gov.hmrc.http.HttpReads.Implicits.*

  val aucdBaseUrl = appConfig.agentUserClientDetailsBaseUrl
  private val aucdUrl = s"$aucdBaseUrl/agent-user-client-details"

  override def agentSize(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[Int]] = {
    val url = s"$aucdUrl/arn/${arn.value}/agent-size"
    httpV2
      .get(url"$url")
      .transform(ws => ws.withRequestTimeout(3.minutes))
      .execute[AgentClientSize]
      .map { response =>
        response.`client-count`
      }
      .map(Option(_))
      .recover { case UpstreamErrorResponse(message, upstreamResponseCode, _, _) =>
        logger.warn(s"Received $upstreamResponseCode status: $message")
        Option.empty[Int]
      }
  }

  override def clientCountByTaxService(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Map[String, Int]]] = {
    val url = s"$aucdUrl/arn/${arn.value}/tax-service-client-count"

    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case OK    => Option(response.json.as[Map[String, Int]])
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def isSingleUserAgency(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/user-check"

    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case NO_CONTENT =>
          Option(false)
        case FORBIDDEN =>
          Option(true)
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def outstandingWorkItemsExist(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/work-items-exist"
    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          Option(true)
        case NO_CONTENT =>
          Option(false)
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def outstandingAssignmentsWorkItemsExist(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/assignments-work-items-exist"
    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          Option(true)
        case NO_CONTENT =>
          Option(false)
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def getClients(arn: Arn, sendEmail: Boolean = false, lang: Option[String] = None)(using
    RequestHeader,
    ExecutionContext
  ): Future[Option[Seq[Client]]] = {

    val params = if sendEmail then "?sendEmail=true" + lang.fold("")("&lang=" + _) else ""
    val url = s"$aucdUrl/arn/${arn.value}/client-list$params"
    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case ACCEPTED | OK =>
          response.json.asOpt[Seq[Client]]
        case other if is5xx(other) =>
          logger.error(s"Received $other status: ${response.body}")
          throw UpstreamErrorResponse("Error fetching clients from backend", other)
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String], filter: Option[String])(using
    RequestHeader,
    ExecutionContext
  ): Future[PaginatedList[Client]] = {

    val searchParam = search.fold("")(searchTerm => s"&search=$searchTerm")
    val filterParam = filter.fold("")(filterTerm => s"&filter=$filterTerm")
    val url = s"$aucdUrl/arn/${arn.value}/clients" +
      s"?page=$page&pageSize=$pageSize$searchParam$filterParam"

    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.as[PaginatedList[Client]]
        case e  => throw UpstreamErrorResponse(s"error getClientList for ${arn.value}", e)
      }
    }
  }

  override def getClientListStatus(
    arn: Arn
  )(using RequestHeader, ExecutionContext): Future[Option[Int]] = {

    val url = s"$aucdUrl/arn/${arn.value}/client-list-status"

    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case ACCEPTED | OK =>
          Some(response.status)
        case other if is5xx(other) =>
          logger.error(s"Received $other status: ${response.body}")
          throw UpstreamErrorResponse("Error fetching client list status from backend", other)
        case other =>
          logger.warn(s"Received $other status: ${response.body}")
          None
      }
    }
  }

  override def pushAssignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(using RequestHeader, ExecutionContext): Future[EacdAssignmentsPushStatus] = {

    val url = s"$aucdUrl/user-enrolment-assignments"
    httpV2
      .post(url"$url")
      .withBody(Json.toJson(userEnrolmentAssignments))
      .execute[HttpResponse]
      .transformWith {
        case Success(response) =>
          response.status match {
            case ACCEPTED =>
              Future.successful(AssignmentsPushed)
            case other =>
              logger.warn(s"EACD assignments not pushed. Received $other status: ${response.body}")
              Future.successful(AssignmentsNotPushed)
          }
        case Failure(ex) =>
          logger.error(s"EACD assignments not pushed. Error: ${ex.getMessage}")
          Future.successful(AssignmentsNotPushed)
      }
  }

  def getTeamMembers(arn: Arn)(using RequestHeader, ExecutionContext): Future[Seq[UserDetails]] = {
    val url = s"$aucdUrl/arn/${arn.value}/team-members"
    httpV2.get(url"$url").execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.as[Seq[UserDetails]]
        case e  => throw UpstreamErrorResponse(s"error getTeamMemberList for ${arn.value}", e)
      }
    }
  }

  def syncTeamMember(arn: Arn, userId: String, expectedAssignments: Seq[String] /* enrolment keys */ )(using
    RequestHeader,
    ExecutionContext
  ): Future[Boolean] = {
    val url = s"$aucdBaseUrl/agent-user-client-details/arn/${arn.value}/user/$userId/ensure-assignments"
    httpV2
      .post(url"$url")
      .withBody(Json.toJson(expectedAssignments))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK        => Future.successful(false)
          case ACCEPTED  => Future.successful(true)
          case NOT_FOUND =>
            logger.warn(s"Team member assignment failed: Not found $arn / $userId")
            Future.failed(new NotFoundException(response.body))
          case other =>
            logger.warn(s"Team member assignment sync returned unexpected status $other: ${response.body}")
            Future.failed(new HttpException(response.body, other))
        }
      }
  }
}
