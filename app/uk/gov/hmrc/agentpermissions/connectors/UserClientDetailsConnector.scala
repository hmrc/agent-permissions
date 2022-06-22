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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserClientDetailsConnectorImpl])
trait UserClientDetailsConnector {
  def agentSize(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Int]]

  def isSingleUserAgency(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def outstandingWorkItemsExist(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

  def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Client]]]
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

  override def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Client]]] = {
    val url = new URL(aucdBaseUrl, s"/agent-user-client-details/arn/${arn.value}/client-list")

    monitor("ConsumedAPI-getClientList-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case ACCEPTED =>
            None
          case OK =>
            response.json.asOpt[Seq[Client]]
          case other =>
            logger.warn(s"Received $other status: ${response.body}")
            None
        }
      }
    }
  }

}
