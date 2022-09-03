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

package uk.gov.hmrc.agentpermissions.service.audit

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentsSplitter
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def auditAccessGroupCreation(accessGroup: AccessGroup)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupUpdate(accessGroup: AccessGroup)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupDeletion(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def auditEsAssignmentUnassignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditOptInEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditOptOutEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupClientsRemoval(accessGroup: AccessGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def auditAccessGroupTeamMembersRemoval(accessGroup: AccessGroup, teamMembersToRemove: Set[AgentUser])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit
}

@Singleton
class AuditServiceImpl @Inject() (auditConnector: AuditConnector)(implicit appConfig: AppConfig)
    extends AuditService with Logging {

  override def auditAccessGroupCreation(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupCreated",
      AccessGroupSplitter.split(accessGroup, appConfig.accessGroupChunkSize)
    )

  override def auditAccessGroupUpdate(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupUpdated",
      AccessGroupSplitter.split(accessGroup, appConfig.accessGroupChunkSize)
    )

  override def auditAccessGroupDeletion(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendAuditEvent(
      "GranularPermissionsAccessGroupDeleted",
      Json.obj("arn" -> s"${arn.value}", "groupName" -> s"$groupName", "user" -> agentUser)
    )

  override def auditEsAssignmentUnassignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val splitUserEnrolmentAssignments =
      UserEnrolmentAssignmentsSplitter.split(userEnrolmentAssignments, appConfig.useEnrolmentAssignmentsChunkSize)

    sendChunkedAuditEvents("GranularPermissionsESAssignmentsUnassignmentsPushed", splitUserEnrolmentAssignments)
  }

  override def auditOptInEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendAuditEvent("GranularPermissionsOptedIn", Json.obj("arn" -> s"${arn.value}", "user" -> agentUser))

  override def auditOptOutEvent(arn: Arn, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendAuditEvent("GranularPermissionsOptedOut", Json.obj("arn" -> s"${arn.value}", "user" -> agentUser))

  override def auditAccessGroupClientsRemoval(accessGroup: AccessGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupClientsRemoval",
      AccessGroupClientsRemoval
        .split(accessGroup._id, accessGroup.groupName, clientsToRemove, chunkSize = appConfig.clientsRemovalChunkSize)
    )

  override def auditAccessGroupTeamMembersRemoval(
    accessGroup: AccessGroup,
    teamMembersToRemove: Set[AgentUser]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupTeamMembersRemoval",
      AccessGroupTeamMembersRemoval.split(
        accessGroup._id,
        accessGroup.groupName,
        teamMembersToRemove,
        chunkSize = appConfig.teamMembersRemovalChunkSize
      )
    )

  private def sendChunkedAuditEvents[T](eventType: String, chunks: Seq[T])(implicit
    writes: Writes[T],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    AuditEventBatch.make(chunks).parts.foreach { auditEventBatchPart =>
      sendAuditEvent(eventType, auditEventBatchPart.json)
    }

  private def sendAuditEvent(eventType: String, detail: JsObject)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    auditConnector.sendExplicitAudit(auditType = eventType, detail = detail)

}

case class AuditEventBatchPart[T](value: T, batchId: String, partNumber: Int, partOutOfTotal: Int) {

  def json(implicit writes: Writes[T]): JsObject =
    Json.obj(
      "batchPart" -> Json
        .obj("batchId" -> batchId, "partNumber" -> partNumber, "partOutOfTotal" -> partOutOfTotal, "value" -> value)
    )
}

case class AuditEventBatch[T](parts: Seq[AuditEventBatchPart[T]])

object AuditEventBatch {
  def make[T](items: Seq[T]): AuditEventBatch[T] = {
    val id = UUID.randomUUID().toString
    AuditEventBatch(items.zipWithIndex.map(i => AuditEventBatchPart(i._1, id, i._2 + 1, items.size)))
  }
}
