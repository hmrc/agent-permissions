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
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.model.PaginatedList
import uk.gov.hmrc.agentpermissions.model.UserEnrolmentAssignments
import uk.gov.hmrc.agentpermissions.model.accessgroups.Client
import uk.gov.hmrc.agentpermissions.model.accessgroups.UserDetails
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions.is5xx
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

@ImplementedBy(classOf[UserClientDetailsConnectorImpl])
trait UserClientDetailsConnector {
  def agentSize(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]]

  def isSingleUserAgency(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def outstandingWorkItemsExist(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def outstandingAssignmentsWorkItemsExist(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]
  def getClients(arn: Arn, sendEmail: Boolean = false, lang: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Seq[Client]]]

  def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[Client]]
  def pushAssignments(
    assignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus]

  def getClientListStatus(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]]

  def clientCountByTaxService(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Map[String, Int]]]

  def getTeamMembers(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]]

  def syncTeamMember(arn: Arn, userId: String, expectedAssignments: Seq[String] /* enrolment keys */ )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean]
}

case class AgentClientSize(`client-count`: Int)

object AgentClientSize {
  implicit val format: OFormat[AgentClientSize] = Json.format[AgentClientSize]
}

@Singleton
class UserClientDetailsConnectorImpl @Inject() (httpV2: HttpClientV2, val ec: ExecutionContext)(implicit
  appConfig: AppConfig
) extends UserClientDetailsConnector with Logging {

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val aucdBaseUrl = appConfig.agentUserClientDetailsBaseUrl
  private val aucdUrl = s"$aucdBaseUrl/agent-user-client-details"

  override def agentSize(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]] = {
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
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Map[String, Int]]] = {
    val url = s"$aucdUrl/arn/${arn.value}/tax-service-client-count"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => Option(response.json.as[Map[String, Int]])
          case other =>
            logger.warn(s"Received $other status: ${response.body}")
            None
        }
      }
  }

  override def isSingleUserAgency(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/user-check"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
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
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/work-items-exist"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
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
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = s"$aucdUrl/arn/${arn.value}/assignments-work-items-exist"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
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

  override def getClients(arn: Arn, sendEmail: Boolean = false, lang: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Seq[Client]]] = {

    val params = if (sendEmail) "?sendEmail=true" + lang.fold("")("&lang=" + _) else ""
    val url = s"$aucdUrl/arn/${arn.value}/client-list$params"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
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

  def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[Client]] = {

    val searchParam = search.fold("")(searchTerm => s"&search=$searchTerm")
    val filterParam = filter.fold("")(filterTerm => s"&filter=$filterTerm")
    val url = s"$aucdUrl/arn/${arn.value}/clients" +
      s"?page=$page&pageSize=$pageSize$searchParam$filterParam"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => response.json.as[PaginatedList[Client]]
          case e  => throw UpstreamErrorResponse(s"error getClientList for ${arn.value}", e)
        }
      }
  }

  override def getClientListStatus(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]] = {
    val url = s"$aucdUrl/arn/${arn.value}/client-list-status"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
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
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus] = {
    val url = s"$aucdUrl/user-enrolment-assignments"
    httpV2
      .post(url"$url")
      .withBody(Json.toJson(userEnrolmentAssignments))
      .execute[HttpResponse]
      .transformWith {
        case Success(response) =>
          response.status match {
            case ACCEPTED =>
              Future successful AssignmentsPushed
            case other =>
              logger.warn(s"EACD assignments not pushed. Received $other status: ${response.body}")
              Future successful AssignmentsNotPushed
          }
        case Failure(ex) =>
          logger.error(s"EACD assignments not pushed. Error: ${ex.getMessage}")
          Future successful AssignmentsNotPushed
      }

  }

  def getTeamMembers(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]] = {
    val url = s"$aucdUrl/arn/${arn.value}/team-members"
    httpV2
      .get(url"$url")
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => response.json.as[Seq[UserDetails]]
          case e  => throw UpstreamErrorResponse(s"error getTeamMemberList for ${arn.value}", e)
        }
      }
  }

  def syncTeamMember(arn: Arn, userId: String, expectedAssignments: Seq[String] /* enrolment keys */ )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] = {
    val url = s"$aucdBaseUrl/agent-user-client-details/arn/${arn.value}/user/$userId/ensure-assignments"
    httpV2
      .post(url"$url")
      .withBody(Json.toJson(expectedAssignments))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK       => Future.successful(false)
          case ACCEPTED => Future.successful(true)
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

sealed trait EacdAssignmentsPushStatus
case object AssignmentsPushed extends EacdAssignmentsPushStatus
case object AssignmentsNotPushed extends EacdAssignmentsPushStatus
