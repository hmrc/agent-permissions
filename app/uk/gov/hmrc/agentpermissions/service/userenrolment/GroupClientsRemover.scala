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
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[GroupClientsRemoverImpl])
trait GroupClientsRemover {

  def removeClientsFromGroup(
    accessGroup: AccessGroup,
    removalEnrolments: Set[Enrolment],
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
    removalEnrolments: Set[Enrolment],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): AccessGroup =
    accessGroup.clients match {
      case None =>
        accessGroup
      case Some(enrolmentsOfAccessGroup) =>
        val enrolmentsToRemoveFromAccessGroup =
          findEnrolmentsToRemoveFromAccessGroup(enrolmentsOfAccessGroup, removalEnrolments)

        if (enrolmentsToRemoveFromAccessGroup.nonEmpty) {
          modifyAccessGroupWithClientsRemoved(
            accessGroup,
            enrolmentsOfAccessGroup,
            enrolmentsToRemoveFromAccessGroup,
            whoIsUpdating
          )
        } else {
          accessGroup
        }
    }

  private def findEnrolmentsToRemoveFromAccessGroup(
    enrolmentsOfAccessGroup: Set[Enrolment],
    removalEnrolments: Set[Enrolment]
  ): Set[Enrolment] =
    enrolmentsOfAccessGroup.foldLeft(Set.empty[Enrolment]) { (acc, enrolmentOfAccessGroup) =>
      removalEnrolments.find(removalEnrolment =>
        removalEnrolment.service == enrolmentOfAccessGroup.service && removalEnrolment.identifiers == enrolmentOfAccessGroup.identifiers
      ) match {
        case None        => acc
        case Some(found) => acc + found
      }
    }

  private def modifyAccessGroupWithClientsRemoved(
    accessGroup: AccessGroup,
    enrolmentsOfAccessGroup: Set[Enrolment],
    enrolmentsToRemoveFromAccessGroup: Set[Enrolment],
    whoIsUpdating: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): AccessGroup = {

    val (enrolmentsToRemove, enrolmentsToKeep): (Set[Enrolment], Set[Enrolment]) =
      enrolmentsOfAccessGroup.partition(enrolment =>
        enrolmentsToRemoveFromAccessGroup.exists(enrolmentToRemove =>
          enrolmentToRemove.service == enrolment.service && enrolmentToRemove.identifiers == enrolment.identifiers
        )
      )

    auditAccessGroupClientsRemoval(accessGroup, enrolmentsToRemove)

    accessGroup.copy(
      lastUpdated = LocalDateTime.now(),
      lastUpdatedBy = whoIsUpdating,
      clients = Some(enrolmentsToKeep)
    )
  }

  private def auditAccessGroupClientsRemoval(accessGroup: AccessGroup, enrolmentsToRemove: Set[Enrolment])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    auditConnector.sendExplicitAudit(
      "AccessGroupClientsRemoval",
      Json.obj(
        "accessGroupId"   -> s"${accessGroup._id}",
        "accessGroupName" -> s"${accessGroup.groupName}",
        "clientsRemoved"  -> enrolmentsToRemove
      )
    )
}
