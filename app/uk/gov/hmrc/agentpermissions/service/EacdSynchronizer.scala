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
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn}
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.EacdSyncRepository
import uk.gov.hmrc.agentpermissions.service.userenrolment.AccessGroupSynchronizer
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EacdSynchronizerImpl])
trait EacdSynchronizer {
  def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class EacdSynchronizerImpl @Inject() (
  userClientDetailsConnector: UserClientDetailsConnector,
  accessGroupSynchronizer: AccessGroupSynchronizer,
  eacdSyncRepository: EacdSyncRepository,
  appConfig: AppConfig
) extends EacdSynchronizer with Logging {

  override def syncWithEacd(arn: Arn, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    for {
      maybeOutstandingAssignmentsWorkItemsExist <-
        userClientDetailsConnector.outstandingAssignmentsWorkItemsExist(arn)

      maybeEacdSyncRecord <- maybeOutstandingAssignmentsWorkItemsExist match {
                               case None => Future successful None
                               case Some(outstandingAssignmentsWorkItemsExist) =>
                                 if (outstandingAssignmentsWorkItemsExist) Future.successful(None)
                                 else eacdSyncRepository.acquire(arn, appConfig.eacdSyncNotBeforeSeconds)
                             }

      maybeGroupDelegatedEnrolments <- maybeEacdSyncRecord match {
                                         case None =>
                                           logger.debug(s"Skipping EACD sync for '${arn.value}'")
                                           Future successful None
                                         case _ =>
                                           logger.info(s"Calling EACD sync for '${arn.value}'")
                                           userClientDetailsConnector.getClientsWithAssignedUsers(arn)

                                       }

      updateStatuses <- maybeGroupDelegatedEnrolments match {
                          case None =>
                            Future successful Seq.empty[AccessGroupUpdateStatus]
                          case Some(groupDelegatedEnrolments) =>
                            accessGroupSynchronizer.syncWithEacd(arn, groupDelegatedEnrolments, whoIsUpdating)
                        }
    } yield updateStatuses

}
