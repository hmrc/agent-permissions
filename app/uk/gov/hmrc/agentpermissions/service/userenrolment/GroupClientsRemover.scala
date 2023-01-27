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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client, CustomGroup}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[GroupClientsRemoverImpl])
trait GroupClientsRemover {

  def removeClientsFromGroup(
    accessGroup: CustomGroup,
    removalEnrolmentKeys: Set[String],
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): CustomGroup
}

@Singleton
class GroupClientsRemoverImpl @Inject() (auditService: AuditService) extends GroupClientsRemover with Logging {

  override def removeClientsFromGroup(
    accessGroup: CustomGroup,
    removalEnrolmentKeys: Set[String],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): CustomGroup =
    accessGroup.clients match {
      case None =>
        accessGroup
      case Some(clientsOfAccessGroup) =>
        val clientsToRemoveFromAccessGroup =
          findClientsToRemoveFromAccessGroup(clientsOfAccessGroup, removalEnrolmentKeys)

        if (clientsToRemoveFromAccessGroup.nonEmpty) {
          modifyAccessGroupWithClientsRemoved(
            accessGroup,
            clientsOfAccessGroup,
            clientsToRemoveFromAccessGroup,
            whoIsUpdating
          )
        } else {
          accessGroup
        }
    }

  private def findClientsToRemoveFromAccessGroup(
    clientsOfAccessGroup: Set[Client],
    removalEnrolmentKeys: Set[String]
  ): Set[Client] =
    clientsOfAccessGroup.foldLeft(Set.empty[Client]) { (acc, clientOfAccessGroup) =>
      removalEnrolmentKeys.find(_ == clientOfAccessGroup.enrolmentKey) match {
        case None    => acc
        case Some(_) => acc + clientOfAccessGroup
      }
    }

  private def modifyAccessGroupWithClientsRemoved(
    accessGroup: CustomGroup,
    clientsOfAccessGroup: Set[Client],
    clientsToRemoveFromAccessGroup: Set[Client],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): CustomGroup = {

    val (clientsToRemove, clientsToKeep): (Set[Client], Set[Client]) =
      clientsOfAccessGroup.partition(client =>
        clientsToRemoveFromAccessGroup.exists(clientToRemove => client.enrolmentKey == clientToRemove.enrolmentKey)
      )

    auditService.auditAccessGroupClientsRemoval(accessGroup, clientsToRemove)

    accessGroup.copy(
      lastUpdated = LocalDateTime.now(),
      lastUpdatedBy = whoIsUpdating,
      clients = Some(clientsToKeep)
    )
  }

}
