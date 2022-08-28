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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Client}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[GroupClientsRemoverImpl])
trait GroupClientsRemover {

  def removeClientsFromGroup(
    accessGroup: AccessGroup,
    removalEnrolmentKeys: Set[String],
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): AccessGroup
}

@Singleton
class GroupClientsRemoverImpl @Inject() (auditConnector: AuditConnector) extends GroupClientsRemover with Logging {

  override def removeClientsFromGroup(
    accessGroup: AccessGroup,
    removalEnrolmentKeys: Set[String],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): AccessGroup =
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
    accessGroup: AccessGroup,
    clientsOfAccessGroup: Set[Client],
    clientsToRemoveFromAccessGroup: Set[Client],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): AccessGroup = {

    val (clientsToRemove, clientsToKeep): (Set[Client], Set[Client]) =
      clientsOfAccessGroup.partition(client =>
        clientsToRemoveFromAccessGroup.exists(clientToRemove => client.enrolmentKey == clientToRemove.enrolmentKey)
      )

    auditAccessGroupClientsRemoval(accessGroup, clientsToRemove)

    accessGroup.copy(
      lastUpdated = LocalDateTime.now(),
      lastUpdatedBy = whoIsUpdating,
      clients = Some(clientsToKeep)
    )
  }

  private def auditAccessGroupClientsRemoval(accessGroup: AccessGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    auditConnector.sendExplicitAudit(
      "AccessGroupClientsRemoval",
      Json.obj(
        "accessGroupId"   -> s"${accessGroup._id}",
        "accessGroupName" -> s"${accessGroup.groupName}",
        "clientsRemoved"  -> clientsToRemove
      )
    )
}
