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

package uk.gov.hmrc.agentpermissions.service.audit

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.UserEnrolmentAssignments
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentOps
import uk.gov.hmrc.agents.accessgroups.{AccessGroup, AgentUser, Client, CustomGroup, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {
  def auditOptInEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditOptOutEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupCreation(accessGroup: AccessGroup)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupUpdate(accessGroup: AccessGroup)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupDeletion(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def auditEsAssignmentUnassignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

  def auditAccessGroupClientsRemoval(customGroup: CustomGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def auditAccessGroupExcludedClientsRemoval(tax: TaxGroup, clientsToRemove: Set[Client])(implicit
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
      accessGroup match {
        case cg: CustomGroup =>
          AccessGroupSplitter
            .forCustom(cg, appConfig.accessGroupChunkSize)
            .map(_ =>
              AuditAccessGroup(
                accessGroup.id,
                accessGroup.arn.value,
                accessGroup.groupName,
                accessGroup.created,
                accessGroup.lastUpdated,
                accessGroup.createdBy,
                accessGroup.lastUpdatedBy,
                "custom",
                None,
                cg.teamMembers,
                cg.clients
              )
            )
        case tg: TaxGroup =>
          AccessGroupSplitter
            .forTax(tg, appConfig.accessGroupChunkSize)
            .map(_ =>
              AuditAccessGroup(
                accessGroup.id,
                accessGroup.arn.value,
                accessGroup.groupName,
                accessGroup.created,
                accessGroup.lastUpdated,
                accessGroup.createdBy,
                accessGroup.lastUpdatedBy,
                "tax",
                Some(tg.service),
                tg.teamMembers,
                tg.excludedClients
              )
            )
        case other => throw new RuntimeException(s"Access group is not a CustomGroup or TaxGroup: ${other.toString}")
      }
    )

  override def auditAccessGroupUpdate(
    accessGroup: AccessGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupUpdated",
      accessGroup match {
        case cg: CustomGroup =>
          AccessGroupSplitter
            .forCustom(cg, appConfig.accessGroupChunkSize)
            .map(accessGroup =>
              AuditAccessGroup(
                accessGroup.id,
                accessGroup.arn.value,
                accessGroup.groupName,
                accessGroup.created,
                accessGroup.lastUpdated,
                accessGroup.createdBy,
                accessGroup.lastUpdatedBy,
                "custom",
                None,
                cg.teamMembers,
                cg.clients
              )
            )
        case tg: TaxGroup =>
          AccessGroupSplitter
            .forTax(tg, appConfig.accessGroupChunkSize)
            .map(accessGroup =>
              AuditAccessGroup(
                accessGroup.id,
                accessGroup.arn.value,
                accessGroup.groupName,
                accessGroup.created,
                accessGroup.lastUpdated,
                accessGroup.createdBy,
                accessGroup.lastUpdatedBy,
                "tax",
                Some(tg.service),
                tg.teamMembers,
                tg.excludedClients
              )
            )
        case other => throw new RuntimeException(s"Access group is not a CustomGroup or TaxGroup: ${other.toString}")
      }
    )

  override def auditAccessGroupDeletion(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendAuditEvent(
      "GranularPermissionsAccessGroupDeleted",
      Json.obj("agentReferenceNumber" -> s"${arn.value}", "groupName" -> s"$groupName", "user" -> agentUser)
    )

  override def auditEsAssignmentUnassignments(
    userEnrolmentAssignments: UserEnrolmentAssignments
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val splitUserEnrolmentAssignments =
      UserEnrolmentAssignmentOps
        .split(userEnrolmentAssignments, appConfig.useEnrolmentAssignmentsChunkSize)
        .map(userEnrolmentAssignments =>
          AuditUserEnrolmentAssignments(
            userEnrolmentAssignments.assign,
            userEnrolmentAssignments.unassign,
            userEnrolmentAssignments.arn.value
          )
        )

    sendChunkedAuditEvents("GranularPermissionsESAssignmentsUnassignmentsPushed", splitUserEnrolmentAssignments)
  }

  override def auditOptInEvent(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendAuditEvent(
      "GranularPermissionsOptedIn",
      Json.obj("agentReferenceNumber" -> s"${arn.value}", "user" -> agentUser)
    )

  override def auditOptOutEvent(arn: Arn, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendAuditEvent(
      "GranularPermissionsOptedOut",
      Json.obj("agentReferenceNumber" -> s"${arn.value}", "user" -> agentUser)
    )

  override def auditAccessGroupClientsRemoval(customGroup: CustomGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupClientsRemoval",
      AccessGroupClientsRemoval
        .split(
          customGroup.arn.value,
          customGroup.id,
          customGroup.groupName,
          clientsToRemove,
          chunkSize = appConfig.clientsRemovalChunkSize
        )
    )

  override def auditAccessGroupExcludedClientsRemoval(taxGroup: TaxGroup, clientsToRemove: Set[Client])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupExcludedClientsRemoval",
      AccessGroupClientsRemoval
        .split(
          taxGroup.arn.value,
          taxGroup.id,
          taxGroup.groupName,
          clientsToRemove,
          chunkSize = appConfig.clientsRemovalChunkSize
        )
    )

  override def auditAccessGroupTeamMembersRemoval(
    accessGroup: AccessGroup,
    teamMembersToRemove: Set[AgentUser]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    sendChunkedAuditEvents(
      "GranularPermissionsAccessGroupTeamMembersRemoval",
      AccessGroupTeamMembersRemoval.split(
        accessGroup.arn.value,
        accessGroup.id,
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
