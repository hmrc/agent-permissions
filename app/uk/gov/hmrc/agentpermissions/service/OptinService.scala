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
import uk.gov.hmrc.agentpermissions.connectors.AgentUserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.*
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.OptinEventType.*
import uk.gov.hmrc.agentpermissions.repository.UpsertType.{RecordInserted, RecordUpdated}
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, UpsertType}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.util.RequestAwareLogging

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OptinServiceImpl])
trait OptinService {

  def optin(arn: Arn, user: AgentUser, lang: Option[String])(using
    RequestHeader,
    ExecutionContext
  ): Future[Option[OptinRequestStatus]]

  def optout(arn: Arn, user: AgentUser)(using RequestHeader, ExecutionContext): Future[Option[OptoutRequestStatus]]

  def optinStatus(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[OptinStatus]]

  def optinRecordExists(arn: Arn)(using RequestHeader, ExecutionContext): Future[Boolean]

  def getAll(): Future[Seq[OptinRecord]]
}

@Singleton
class OptinServiceImpl @Inject() (
  optinRepository: OptinRepository,
  optinRecordBuilder: OptinRecordBuilder,
  optedInStatusHandler: OptedInStatusHandler,
  notOptedInStatusHandler: NotOptedInStatusHandler,
  agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
  auditService: AuditService
) extends OptinService with RequestAwareLogging {

  override def optin(arn: Arn, user: AgentUser, lang: Option[String])(using
    RequestHeader,
    ExecutionContext
  ): Future[Option[OptinRequestStatus]] =
    for {
      maybeUpsertType <- handleOptinOptout(arn, user, OptedIn, lang)
      _               <- Future.successful(maybeUpsertType.foreach(_ => auditService.auditOptInEvent(arn, user)))
    } yield maybeUpsertType.map {
      case RecordInserted(_) => OptinRequestStatus.OptinCreated
      case RecordUpdated     => OptinRequestStatus.OptinUpdated
    }

  override def optout(arn: Arn, user: AgentUser)(using
    RequestHeader,
    ExecutionContext
  ): Future[Option[OptoutRequestStatus]] =
    for {
      maybeUpsertType <- handleOptinOptout(arn, user, OptedOut, lang = None)
      _               <- Future.successful(maybeUpsertType.foreach(_ => auditService.auditOptOutEvent(arn, user)))
    } yield maybeUpsertType.map {
      case RecordInserted(_) => OptoutRequestStatus.OptoutCreated
      case RecordUpdated     => OptoutRequestStatus.OptoutUpdated
    }

  override def optinStatus(arn: Arn)(using RequestHeader, ExecutionContext): Future[Option[OptinStatus]] =
    for {
      maybeOptinRecord <- optinRepository.get(arn)
      maybeOptinStatus <- maybeOptinRecord match {
                            case Some(optinRecord) if optinRecord.status == OptedIn =>
                              optedInStatusHandler.identifyStatus(arn)
                            case _ =>
                              notOptedInStatusHandler.identifyStatus(arn)
                          }
    } yield maybeOptinStatus

  override def optinRecordExists(arn: Arn)(using RequestHeader, ExecutionContext): Future[Boolean] =
    for {
      maybeOptinRecord <- optinRepository.get(arn)
    } yield maybeOptinRecord.fold(false)(_.status == OptedIn)

  override def getAll(): Future[Seq[OptinRecord]] = optinRepository.getAll()

  private def handleOptinOptout(arn: Arn, agentUser: AgentUser, optinEventType: OptinEventType, lang: Option[String])(
    using
    RequestHeader,
    ExecutionContext
  ): Future[Option[UpsertType]] =
    for {
      maybeExistingOptinRecord <- optinRepository.get(arn)
      maybeUpdateOptinRecord   <-
        Future.successful(optinRecordBuilder.forUpdating(arn, agentUser, maybeExistingOptinRecord, optinEventType))
      maybeUpsertResult <- maybeUpdateOptinRecord match {
                             case None =>
                               Future.successful(None)
                             case Some(optinRecordToUpdate) =>
                               optinRepository.upsert(optinRecordToUpdate)
                           }
      _ <- agentUserClientDetailsConnector.getClients(arn, sendEmail = optinEventType == OptedIn, lang = lang)
    } yield maybeUpsertResult

}

enum OptinRequestStatus {
  case OptinCreated
  case OptinUpdated
}

enum OptoutRequestStatus {
  case OptoutCreated
  case OptoutUpdated
}
