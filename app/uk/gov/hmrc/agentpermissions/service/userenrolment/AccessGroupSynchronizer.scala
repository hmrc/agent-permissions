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
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.{AccessGroupNotUpdated, AccessGroupUpdateStatus, AccessGroupUpdated}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class RemovalSet(enrolmentKeysToRemove: Set[String], userIdsToRemove: Set[String])

@ImplementedBy(classOf[AccessGroupSynchronizerImpl])
trait AccessGroupSynchronizer {
  def syncWithEacd(
    arn: Arn,
    groupDelegatedEnrolments: GroupDelegatedEnrolments,
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]]
}

@Singleton
class AccessGroupSynchronizerImpl @Inject() (
  accessGroupsRepository: AccessGroupsRepository,
  groupClientsRemover: GroupClientsRemover,
  groupTeamMembersRemover: GroupTeamMembersRemover
) extends AccessGroupSynchronizer with Logging {

  override def syncWithEacd(
    arn: Arn,
    groupDelegatedEnrolments: GroupDelegatedEnrolments,
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    for {
      accessGroups <- accessGroupsRepository.get(arn)
      removalSet = calculateRemovalSet(accessGroups, groupDelegatedEnrolments)
      accessGroupsWithUpdates <-
        applyRemovalsOnAccessGroups(accessGroups, removalSet, whoIsUpdating)
      groupUpdateStatuses <- persistAccessGroups(accessGroupsWithUpdates)
    } yield groupUpdateStatuses

  def calculateRemovalSet(
    accessGroups: Seq[AccessGroup],
    groupDelegatedEnrolments: GroupDelegatedEnrolments
  ): RemovalSet = {
    val globalGroupView: Set[UserEnrolment] = userEnrolmentsOf(accessGroups)
    val eacdAllocatedView: Set[UserEnrolment] = userEnrolmentsOf(groupDelegatedEnrolments)

    val enrolmentKeysToRemove = globalGroupView.map(_.enrolmentKey).diff(eacdAllocatedView.map(_.enrolmentKey))
    val userIdsToRemove = globalGroupView.map(_.userId).diff(eacdAllocatedView.map(_.userId))

    val removalSet = RemovalSet(enrolmentKeysToRemove, userIdsToRemove)
    logger.info(s"Calculated removal set: $removalSet")
    removalSet
  }

  def applyRemovalsOnAccessGroups(
    existingAccessGroups: Seq[AccessGroup],
    removalSet: RemovalSet,
    whoIsUpdating: AgentUser
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroup]] = {

    val removalEnrolments: Set[Enrolment] = buildEnrolmentsFromKeys(removalSet.enrolmentKeysToRemove).flatten
    val removalUserIds: Set[String] = removalSet.userIdsToRemove

    Future successful existingAccessGroups
      .map(groupClientsRemover.removeClientsFromGroup(_, removalEnrolments, whoIsUpdating))
      .map(groupTeamMembersRemover.removeTeamMembersFromGroup(_, removalUserIds, whoIsUpdating))
  }

  def persistAccessGroups(accessGroups: Seq[AccessGroup])(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupUpdateStatus]] =
    Future.sequence(accessGroups.map { accessGroup =>
      logger.info(s"Updating access group of ${accessGroup.arn} having name '${accessGroup.groupName}'")
      accessGroupsRepository
        .update(accessGroup.arn, accessGroup.groupName, accessGroup)
        .map {
          case None =>
            AccessGroupNotUpdated
          case Some(updatedCount) =>
            if (updatedCount == 1L) {
              AccessGroupUpdated
            } else {
              AccessGroupNotUpdated
            }
        }
    })

  private def userEnrolmentsOf(accessGroups: Seq[AccessGroup]): Set[UserEnrolment] =
    (for {
      accessGroup <- accessGroups
      enrolment   <- accessGroup.clients.toSeq.flatten
      identifier  <- enrolment.identifiers
      agentUser   <- accessGroup.teamMembers.toSeq.flatten
    } yield UserEnrolment(agentUser.id, EnrolmentKey.enrolmentKey(enrolment.service, identifier.value))).toSet

  private def userEnrolmentsOf(groupDelegatedEnrolments: GroupDelegatedEnrolments): Set[UserEnrolment] =
    (for {
      assignedClient <- groupDelegatedEnrolments.clients
      identifier     <- assignedClient.identifiers
    } yield UserEnrolment(
      assignedClient.assignedTo,
      EnrolmentKey.enrolmentKey(assignedClient.serviceName, identifier.value)
    )).toSet

  private def buildEnrolmentsFromKeys(enrolmentKeysToRemove: Set[String]): Set[Option[Enrolment]] =
    enrolmentKeysToRemove.map { enrolmentKeyToRemove =>
      Try {
        val parts = enrolmentKeyToRemove.split("~")
        Enrolment(parts(0), "", "", Seq(Identifier(parts(1), parts(2))))
      } match {
        case Success(enrolment) => Some(enrolment)
        case Failure(ex) =>
          logger.error(s"Unable to parse enrolment '$enrolmentKeyToRemove': ${ex.getMessage}")
          None
      }
    }

}
