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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.TaxGroupsRepositoryV2
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agents.accessgroups.{AgentUser, GroupSummary, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxGroupsServiceImpl])
trait TaxGroupsService {
  def clientCountForAvailableTaxServices(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

  def clientCountForTaxGroups(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

  def getById(id: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def create(
    taxGroup: TaxGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxServiceGroupCreationStatus]

  def getAllTaxServiceGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxGroup]]

  // unused? get(groupId) seems to be the same as getById(id) - will be same for AccessGroupService
  def getByName(arn: Arn, groupName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def getByService(arn: Arn, service: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[TaxGroup]]

  def getTaxGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

  def delete(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupDeletionStatus]

  def update(arn: Arn, groupName: String, taxGroup: TaxGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus]

  def addMemberToGroup(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus]

  def removeTeamMember(groupId: GroupId, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus]

}

@Singleton
class TaxGroupsServiceImpl @Inject() (
  taxServiceGroupsRepository: TaxGroupsRepositoryV2,
  userClientDetailsConnector: UserClientDetailsConnector,
  auditService: AuditService
) extends TaxGroupsService with Logging {

  override def clientCountForAvailableTaxServices(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] =
    userClientDetailsConnector.clientCountByTaxService(arn).flatMap {
      case Some(fullMap) =>
        Future
          .sequence(
            fullMap.map { entry =>
              taxServiceGroupsRepository.groupExistsForTaxService(arn, entry._1).map {
                case true  => None
                case false => Some(entry)
              }
            }.toList
          )
          .map(_.flatten)
          .map(_.toMap)
          .map(combineServicesClientCount)
      case None => Future successful Map[String, Int]()
    }

  override def clientCountForTaxGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] =
    for {
      fullCount         <- userClientDetailsConnector.clientCountByTaxService(arn)
      existingTaxGroups <- getAllTaxServiceGroups(arn)

      taxServiceIds = existingTaxGroups.map(groups => groups.service)
      combinedCount = fullCount.fold(Map.empty[String, Int])(fc => combineServicesClientCount(fc))

      groupCount = combinedCount.filter(m => taxServiceIds.contains(m._1))
    } yield groupCount

  /** For services which act as 1 entity in the frontend but exist as 2 enrolment keys */
  private def combineServicesClientCount(count: Map[String, Int]): Map[String, Int] = {
    val TRUSTS = "HMRC-TERS" // taxable "HMRC-TERS-ORG" and non taxable "HMRC-TERSNT-ORG"
    val CBC = "HMRC-CBC" // uk "HMRC-CBC-ORG" and non uk "HMRC-CBC-NONUK-ORG"

    (count.filterNot(m => m._1.contains(TRUSTS) || m._1.contains(CBC)) // normal 1:1 services
      ++ Map(TRUSTS -> count.filter(m => m._1.contains(TRUSTS)).values.sum) // combines trust counts
      ++ Map(CBC -> count.filter(m => m._1.contains(CBC)).values.sum) // combines cbc counts
    ).filter(_._2 > 0) // removes services if agent has no clients of that type
  }

  override def getById(
    id: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]] =
    taxServiceGroupsRepository
      .findById(id)

  override def create(
    taxGroup: TaxGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxServiceGroupCreationStatus] =
    taxServiceGroupsRepository.getByService(taxGroup.arn, taxGroup.service) flatMap {
      case Some(_) =>
        Future.successful(TaxServiceGroupExistsForCreation)
      case _ =>
        for {
          maybeCreationId <- taxServiceGroupsRepository.insert(taxGroup)
        } yield maybeCreationId match {
          case None =>
            TaxServiceGroupNotCreated
          case Some(creationId) =>
            auditService.auditAccessGroupCreation(taxGroup)
            logger.info(s"Created tax service group. Service: ${taxGroup.service} DB id: '$creationId")
            TaxServiceGroupCreated(creationId)
        }
    }

  override def getAllTaxServiceGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxGroup]] =
    taxServiceGroupsRepository
      .get(arn)

  override def getByName(arn: Arn, groupName: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[TaxGroup]] =
    taxServiceGroupsRepository.get(arn, groupName)

  override def getByService(arn: Arn, service: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[TaxGroup]] =
    taxServiceGroupsRepository
      .getByService(arn, service)

  override def getTaxGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] = for {
    taxGroups <- taxServiceGroupsRepository.get(arn)
    usersGroups = taxGroups.filter(_.teamMembers.map(_.id).contains(userId))
  } yield usersGroups.map(group => GroupSummary.of(group))

  override def delete(arn: Arn, groupName: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupDeletionStatus] =
    for {
      maybeDeletedCount <- taxServiceGroupsRepository.delete(arn, groupName)
      taxServiceGroupDeletionStatus <- maybeDeletedCount match {
                                         case Some(1L) =>
                                           auditService.auditAccessGroupDeletion(arn, groupName, agentUser)
                                           Future.successful(TaxServiceGroupDeleted)
                                         case _ =>
                                           Future.successful(TaxServiceGroupNotDeleted)
                                       }
    } yield taxServiceGroupDeletionStatus

  override def update(arn: Arn, groupName: String, taxGroup: TaxGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus] =
    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(taxGroup, whoIsUpdating)
      maybeUpdatedCount <-
        taxServiceGroupsRepository
          .update(arn, groupName, accessGroupWithWhoIsUpdating)
      accessGroupUpdateStatus <- maybeUpdatedCount match {
                                   case Some(1L) =>
                                     auditService.auditAccessGroupUpdate(accessGroupWithWhoIsUpdating)
                                     Future.successful(TaxServiceGroupUpdated)
                                   case _ =>
                                     logger.info(
                                       s"Tax service group '${taxGroup.groupName}' not updated. Service: ${taxGroup.service}"
                                     )
                                     Future.successful(TaxServiceGroupNotUpdated)
                                 }
    } yield accessGroupUpdateStatus

  private def mergeWhoIsUpdating(
    taxGroup: TaxGroup,
    whoIsUpdating: AgentUser
  ): Future[TaxGroup] =
    Future.successful(taxGroup.copy(lastUpdated = LocalDateTime.now(), lastUpdatedBy = whoIsUpdating))

  override def addMemberToGroup(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus] =
    taxServiceGroupsRepository
      .addTeamMember(groupId, agentUser)
      .map(_.getMatchedCount match {
        case 1 => TaxServiceGroupUpdated
        case _ => TaxServiceGroupNotUpdated
      })

  def removeTeamMember(groupId: GroupId, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus] =
    taxServiceGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeAgentUsers = accessGroup.teamMembers.filterNot(tm => tm.id == teamMemberId)
          val updatedGroup = accessGroup.copy(teamMembers = maybeAgentUsers)
          taxServiceGroupsRepository
            .update(accessGroup.arn, accessGroup.groupName, updatedGroup)
            .map {
              case Some(1) => TaxServiceGroupUpdated
              case _       => TaxServiceGroupNotUpdated
            }
        case None => Future successful TaxServiceGroupNotUpdated
      }
}

sealed trait TaxServiceGroupCreationStatus
case class TaxServiceGroupCreated(creationId: String) extends TaxServiceGroupCreationStatus
case object TaxServiceGroupExistsForCreation extends TaxServiceGroupCreationStatus
case object TaxServiceGroupNotCreated extends TaxServiceGroupCreationStatus

sealed trait TaxServiceGroupDeletionStatus
case object TaxServiceGroupDeleted extends TaxServiceGroupDeletionStatus
case object TaxServiceGroupNotDeleted extends TaxServiceGroupDeletionStatus

sealed trait TaxServiceGroupUpdateStatus
case object TaxServiceGroupNotUpdated extends TaxServiceGroupUpdateStatus
case object TaxServiceGroupUpdated extends TaxServiceGroupUpdateStatus
