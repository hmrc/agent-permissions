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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, GroupDelegatedEnrolments, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.HttpReads.is5xx
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@ImplementedBy(classOf[UserClientDetailsConnectorImpl])
trait UserClientDetailsConnector {
  def agentSize(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]]

  def isSingleUserAgency(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def outstandingWorkItemsExist(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def outstandingAssignmentsWorkItemsExist(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]
  def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Client]]]

  def pushAssignments(
    assignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus]

  def getClientListStatus(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]]

  def getClientsWithAssignedUsers(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[GroupDelegatedEnrolments]]
}

@Singleton
class UserClientDetailsConnectorImpl @Inject() (http: HttpClient, metrics: Metrics)(implicit appConfig: AppConfig)
    extends UserClientDetailsConnector with HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val aucdBaseUrl = new URL(appConfig.agentUserClientDetailsBaseUrl)

  override def agentSize(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/agent-size")

    monitor(s"ConsumedAPI-AgentUserClientDetails-AgentSize-GET") {
      http.GET[HttpResponse](url.toString).map { response =>
        response.status match {
          case OK =>
            Option((response.json \ "client-count").as[Int])
          case other =>
            logger.warn(s"Received $other status: ${response.body}")
            None
        }
      }
    }
  }

  override def isSingleUserAgency(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/user-check")

    monitor(s"ConsumedAPI-AgentUserClientDetails-UserCheck-GET") {
      http.GET[HttpResponse](url.toString).map { response =>
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
  }

  override def outstandingWorkItemsExist(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/work-items-exist")

    monitor(s"ConsumedAPI-AgentUserClientDetails-WorkItemsExist-GET") {
      http.GET[HttpResponse](url.toString).map { response =>
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
  }

  override def outstandingAssignmentsWorkItemsExist(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/assignments-work-items-exist")

    monitor(s"ConsumedAPI-AgentUserClientDetails-AssignmentsWorkItemsExist-GET") {
      http.GET[HttpResponse](url.toString).map { response =>
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
  }

  override def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Client]]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/client-list")

    monitor("ConsumedAPI-AgentUserClientDetails-ClientList-GET") {
      http.GET[HttpResponse](url).map { response =>
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
  }

  override def getClientListStatus(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/client-list-status")

    monitor("ConsumedAPI-AgentUserClientDetails-ClientListStatus-GET") {
      http.GET[HttpResponse](url).map { response =>
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
  }

  override def pushAssignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/user-enrolment-assignments")

    monitor("ConsumedAPI-AgentUserClientDetails-PushAssignments-POST") {
      http.POST[UserEnrolmentAssignments, HttpResponse](url, userEnrolmentAssignments) transformWith {
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
  }

  override def getClientsWithAssignedUsers(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[GroupDelegatedEnrolments]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/clients-assigned-users")

    monitor("ConsumedAPI-AgentUserClientDetails-ClientsWithAssignedUsers-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK =>
            Some(response.json.as[GroupDelegatedEnrolments])
          case other =>
            logger.warn(s"Received $other status: ${response.body}")
            None
        }
      }
    }
  }
}

sealed trait EacdAssignmentsPushStatus
case object AssignmentsPushed extends EacdAssignmentsPushStatus
case object AssignmentsNotPushed extends EacdAssignmentsPushStatus
