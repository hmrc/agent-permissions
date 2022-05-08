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

import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, UpsertType}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OptinService @Inject() (optinRepository: OptinRepository, optinRecordBuilder: OptinRecordBuilder) {

  def optin(arn: Arn, user: AgentUser)(implicit ec: ExecutionContext): Future[Option[UpsertType]] =
    handleOptinOptout(arn, user, OptedIn)

  def optout(arn: Arn, user: AgentUser)(implicit ec: ExecutionContext): Future[Option[UpsertType]] =
    handleOptinOptout(arn, user, OptedOut)

  private def handleOptinOptout(arn: Arn, user: AgentUser, optinEventTypeToMatch: OptinEventType)(implicit
    ec: ExecutionContext
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
    } yield maybeUpsertResult

}
