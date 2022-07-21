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

package uk.gov.hmrc.agentpermissions.service

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, RecordInserted, RecordUpdated, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OptinServiceImpl])
trait OptinService {

  def optin(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[OptinRequestStatus]]

  def optout(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[OptoutRequestStatus]]

  def optinStatus(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[OptinStatus]]

  def optinRecordExists(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean]
}

@Singleton
class OptinServiceImpl @Inject() (
  optinRepository: OptinRepository,
  optinRecordBuilder: OptinRecordBuilder,
  optedInStatusHandler: OptedInStatusHandler,
  notOptedInStatusHandler: NotOptedInStatusHandler,
  userClientDetailsConnector: UserClientDetailsConnector
) extends OptinService with Logging {

  override def optin(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[OptinRequestStatus]] =
    handleOptinOptout(arn, user, OptedIn).map(_.map {
      case RecordInserted(_) => OptinCreated
      case RecordUpdated     => OptinUpdated
    })

  override def optout(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[OptoutRequestStatus]] =
    handleOptinOptout(arn, user, OptedOut).map(_.map {
      case RecordInserted(_) => OptoutCreated
      case RecordUpdated     => OptoutUpdated
    })

  override def optinStatus(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[OptinStatus]] =
    for {
      maybeOptinRecord <- optinRepository.get(arn)
      maybeOptinStatus <- maybeOptinRecord match {
                            case Some(optinRecord) if optinRecord.status == OptedIn =>
                              optedInStatusHandler.identifyStatus(arn)
                            case _ =>
                              notOptedInStatusHandler.identifyStatus(arn)
                          }
    } yield maybeOptinStatus

  override def optinRecordExists(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean] =
    for {
      maybeOptinRecord <- optinRepository.get(arn)
    } yield maybeOptinRecord.fold(false)(_.status == OptedIn)

  private def handleOptinOptout(arn: Arn, user: AgentUser, optinEventTypeToMatch: OptinEventType)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[UpsertType]] =
    for {
      maybeExistingOptinRecord <- optinRepository.get(arn)
      maybeUpdateOptinRecord <-
        Future.successful(optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventTypeToMatch))
      maybeUpsertResult <- maybeUpdateOptinRecord match {
                             case None =>
                               Future.successful(None)
                             case Some(optinRecordToUpdate) =>
                               optinRepository.upsert(optinRecordToUpdate)
                           }
      _ <- userClientDetailsConnector.getClientListStatus(arn)
    } yield maybeUpsertResult
}

sealed trait OptinRequestStatus
case object OptinCreated extends OptinRequestStatus
case object OptinUpdated extends OptinRequestStatus

sealed trait OptoutRequestStatus
case object OptoutCreated extends OptoutRequestStatus
case object OptoutUpdated extends OptoutRequestStatus
