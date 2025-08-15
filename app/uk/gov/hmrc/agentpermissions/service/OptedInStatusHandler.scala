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
import play.api.Logging
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OptedInStatusHandlerImpl])
trait OptedInStatusHandler {
  def identifyStatus(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[OptinStatus]]
}

@Singleton
class OptedInStatusHandlerImpl @Inject() (userClientDetailsConnector: UserClientDetailsConnector)
    extends OptedInStatusHandler with Logging {

  def identifyStatus(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[OptinStatus]] =
    for {
      maybeSingleUser <- userClientDetailsConnector.isSingleUserAgency(arn)
      maybeOptinStatus: Option[OptinStatus] <- {
        maybeSingleUser match {
          case None =>
            Future.successful(None)
          case Some(singleUser) =>
            if (singleUser) {
              Future.successful(Option(OptedInSingleUser))
            } else {
              for {
                maybeWorkItemsExist <- userClientDetailsConnector.outstandingWorkItemsExist(arn)
                maybeOptinStatus <- maybeWorkItemsExist match {
                                      case None =>
                                        Future.successful(None)
                                      case Some(workItemsExist) =>
                                        if (workItemsExist) {
                                          Future.successful(Option(OptedInNotReady))
                                        } else {
                                          Future.successful(Option(OptedInReady))
                                        }
                                    }
              } yield maybeOptinStatus
            }
        }
      }
    } yield maybeOptinStatus

}
