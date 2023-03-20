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
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepository
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

  def getById(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def create(
    taxGroup: TaxGroup
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxServiceGroupCreationStatus]

  def getAllTaxServiceGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxGroup]]

  // unused? get(groupId) seems to be the same as getById(id) - will be same for AccessGroupService
  def get(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def get(arn: Arn, service: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[TaxGroup]]

  def getTaxGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

  def delete(groupId: GroupId, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupDeletionStatus]

  def update(groupId: GroupId, taxGroup: TaxGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus]

  def addMemberToGroup(groupId: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus]

  def removeTeamMember(groupId: String, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus]

}

@Singleton
class TaxGroupsServiceImpl @Inject() (
  taxServiceGroupsRepository: TaxServiceGroupsRepository,
  userClientDetailsConnector: UserClientDetailsConnector
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
          .map(combineTrustClientCount)
      case None => Future successful Map[String, Int]()
    }

  override def clientCountForTaxGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] =
    for {
      fullCount         <- userClientDetailsConnector.clientCountByTaxService(arn)
      existingTaxGroups <- getAllTaxServiceGroups(arn)

      taxServiceIds = existingTaxGroups.map(groups => groups.service)
      combinedCount = fullCount.fold(Map.empty[String, Int])(fc => combineTrustClientCount(fc))

      groupCount = combinedCount.filter(m => taxServiceIds.contains(m._1))
    } yield groupCount

  private val TRUSTS = "HMRC-TERS" // taxable "HMRC-TERS-ORG" and non taxable "HMRC-TERSNT-ORG"

  private def combineTrustClientCount(count: Map[String, Int]): Map[String, Int] = {
    val taxableTrustCounts = count.filter(m => m._1.contains(TRUSTS))
    val combinedTrustCountValue = taxableTrustCounts.values.sum // total

    if (combinedTrustCountValue > 0)
      count.filterNot(m => m._1.contains(TRUSTS)) ++ Map(TRUSTS -> combinedTrustCountValue)
    else count.filterNot(m => m._1.contains(TRUSTS)) // removes trusts if agent has no trust clients
  }

  override def getById(
    id: String
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
          case Some(creationId) => // TODO add auditing via audit service
            logger.info(s"Created tax service group. Service: ${taxGroup.service} DB id: '$creationId")
            TaxServiceGroupCreated(creationId)
        }
    }

  override def getAllTaxServiceGroups(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TaxGroup]] =
    taxServiceGroupsRepository
      .get(arn)

  override def get(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]] =
    taxServiceGroupsRepository
      .get(groupId.arn, groupId.groupName)

  override def get(arn: Arn, service: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[TaxGroup]] =
    taxServiceGroupsRepository
      .getByService(arn, service)

  override def getTaxGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    taxServiceGroupsRepository
      .get(arn)
      .map(accessGroups =>
        accessGroups
          .filter(_.teamMembers.fold(false)(_.map(_.id).contains(userId)))
          .map(group => GroupSummary.fromAccessGroup(group))
      )

  override def delete(
    groupId: GroupId,
    agentUser: AgentUser
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxServiceGroupDeletionStatus] =
    for {
      maybeDeletedCount <- taxServiceGroupsRepository.delete(groupId.arn, groupId.groupName)
      taxServiceGroupDeletionStatus <- maybeDeletedCount match {
                                         case Some(1L) => // TODO add auditing
                                           Future.successful(TaxServiceGroupDeleted)
                                         case _ =>
                                           Future.successful(TaxServiceGroupNotDeleted)
                                       }
    } yield taxServiceGroupDeletionStatus

  override def update(groupId: GroupId, taxGroup: TaxGroup, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus] =
    for {
      accessGroupWithWhoIsUpdating <- mergeWhoIsUpdating(taxGroup, whoIsUpdating)
      maybeUpdatedCount <-
        taxServiceGroupsRepository
          .update(groupId.arn, groupId.groupName, accessGroupWithWhoIsUpdating)
      accessGroupUpdateStatus <- maybeUpdatedCount match {
                                   case Some(1L) =>
//                                         _ <- Future successful auditService.auditAccessGroupUpdate(
//                                                accessGroupWithWhoIsUpdating)
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

  override def addMemberToGroup(groupId: String, agentUser: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[TaxServiceGroupUpdateStatus] =
    taxServiceGroupsRepository
      .addTeamMember(groupId, agentUser)
      .map(_.getMatchedCount match {
        case 1 => TaxServiceGroupUpdated
        case _ => TaxServiceGroupNotUpdated
      })

  def removeTeamMember(groupId: String, teamMemberId: String, whoIsUpdating: AgentUser)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AccessGroupUpdateStatus] =
    taxServiceGroupsRepository
      .findById(groupId)
      .flatMap {
        case Some(accessGroup) =>
          val maybeAgentUsers = accessGroup.teamMembers
            .map(_.filterNot(tm => tm.id == teamMemberId))
          val updatedGroup = accessGroup.copy(teamMembers = maybeAgentUsers)
          taxServiceGroupsRepository
            .update(accessGroup.arn, accessGroup.groupName, updatedGroup)
            .map {
              case Some(1) => AccessGroupUpdated
              case _       => AccessGroupNotUpdated
            }
        case None => Future successful AccessGroupNotUpdated
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
