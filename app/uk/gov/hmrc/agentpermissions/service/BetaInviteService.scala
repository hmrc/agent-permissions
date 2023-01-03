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
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.model.BetaInviteRecord
import uk.gov.hmrc.agentpermissions.repository.{BetaInviteRepository, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BetaInviteServiceImpl])
trait BetaInviteService {

  def hideBetaInvite(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[UpsertType]]

  def hideBetaInviteCheck(arn: Arn, user: AgentUser)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean]
}

@Singleton
class BetaInviteServiceImpl @Inject() (
  betaInviteRepository: BetaInviteRepository,
  betaInviteRecordBuilder: BetaInviteRecordBuilder
) extends BetaInviteService with Logging {

  override def hideBetaInvite(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Option[UpsertType]] =
    for {
      maybeExistingBetaInviteRecord <- betaInviteRepository.get(user)
      maybeUpdateBetaInviteRecord <-
        Future.successful(betaInviteRecordBuilder.forUpdating(arn, user, maybeExistingBetaInviteRecord))
      maybeUpsertResult <- maybeUpdateBetaInviteRecord match {
                             case None =>
                               // record already exists
                               Future.successful(None)
                             case Some(recordToUpdate) =>
                               betaInviteRepository.upsert(recordToUpdate)
                           }

    } yield maybeUpsertResult

  override def hideBetaInviteCheck(arn: Arn, user: AgentUser)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Boolean] =
    for {
      maybeBetaInviteRecord <- betaInviteRepository.get(user)
    } yield maybeBetaInviteRecord.fold(false)(_.hideBetaInvite)

}

@Singleton
class BetaInviteRecordBuilder {

  def forUpdating(
    arn: Arn,
    user: AgentUser,
    maybeExistingRecord: Option[BetaInviteRecord]
  ): Option[BetaInviteRecord] =
    maybeExistingRecord match {
      case None =>
        Option(BetaInviteRecord(arn, user.id, hideBetaInvite = true))
      case Some(_) =>
        None
    }
}
