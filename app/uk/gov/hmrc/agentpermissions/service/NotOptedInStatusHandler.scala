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

package uk.gov.hmrc.agentpermissions.service

import com.google.inject.ImplementedBy
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.AgentUserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.*
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.OptinStatus.*
import uk.gov.hmrc.agentpermissions.util.RequestAwareLogging

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NotOptedInStatusHandlerImpl])
trait NotOptedInStatusHandler {
  def identifyStatus(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[OptinStatus]]
}

@Singleton
class NotOptedInStatusHandlerImpl @Inject() (agentUserClientDetailsConnector: AgentUserClientDetailsConnector)(using
  appConfig: AppConfig
) extends NotOptedInStatusHandler with RequestAwareLogging {

  def identifyStatus(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[OptinStatus]] =
    for {
      maybeSize                             <- agentUserClientDetailsConnector.agentSize(arn)
      maybeOptinStatus: Option[OptinStatus] <-
        maybeSize match {
          case None =>
            Future.successful(None)
          case Some(size) =>
            if size < 2 || size > appConfig.agentSizeMaxClientCountAllowed then
              Future.successful(Option(OptedOutWrongClientCount))
            else
              for {
                maybeSingleUser                       <- agentUserClientDetailsConnector.isSingleUserAgency(arn)
                maybeOptinStatus: Option[OptinStatus] <- {
                  maybeSingleUser match {
                    case None =>
                      Future.successful(None)
                    case Some(isSingleUser) =>
                      if isSingleUser then Future.successful(Option(OptedOutSingleUser))
                      else Future.successful(Option(OptedOutEligible))
                  }
                }
              } yield maybeOptinStatus
        }
    } yield maybeOptinStatus

}
