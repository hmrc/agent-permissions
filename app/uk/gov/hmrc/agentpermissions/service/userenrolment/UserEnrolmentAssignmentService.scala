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
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client, CustomGroup, GroupId, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserEnrolmentAssignmentServiceImpl])
trait UserEnrolmentAssignmentService {

  def calculateForGroupCreation(accessGroup: CustomGroup)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupDeletion(groupId: GroupId)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupUpdate(
    groupId: GroupId,
    accessGroupToUpdate: CustomGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]]

  def calculateForAddToGroup(
    groupId: GroupId,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]]

  def calculateForRemoveFromGroup(
    groupId: GroupId,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]]

  def pushCalculatedAssignments(
    maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus]
}

@Singleton
class UserEnrolmentAssignmentServiceImpl @Inject() (
  accessGroupsRepository: AccessGroupsRepository,
  userEnrolmentAssignmentCalculator: UserEnrolmentAssignmentCalculator,
  userClientDetailsConnector: UserClientDetailsConnector
) extends UserEnrolmentAssignmentService with Logging {

  override def calculateForGroupCreation(
    accessGroup: CustomGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups <- accessGroupsRepository.get(accessGroup.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(userEnrolmentAssignmentCalculator.forGroupCreation(accessGroup, existingAccessGroups))
    } yield maybeUserEnrolmentAssignments

  override def calculateForGroupDeletion(
    groupId: GroupId
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups     <- accessGroupsRepository.get(groupId.arn)
      maybeExistingAccessGroup <- accessGroupsRepository.get(groupId.arn, groupId.groupName)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          maybeExistingAccessGroup.flatMap(userEnrolmentAssignmentCalculator.forGroupDeletion(_, existingAccessGroups))
        )
    } yield maybeUserEnrolmentAssignments

  // TODO APB-7070 - replace with improved method
  override def calculateForGroupUpdate(
    groupId: GroupId,
    accessGroupToUpdate: CustomGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups <- accessGroupsRepository.get(groupId.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(userEnrolmentAssignmentCalculator.forGroupUpdate(accessGroupToUpdate, existingAccessGroups))
    } yield maybeUserEnrolmentAssignments

  /** Could be merged with forRemoveFromGroup if you had a bool for isAssigns
    *
    * One set will always be the add/remove, the other will be the existing set in the group being changed. For example:
    *   - agentUsers to add to group AND existing clients in the group
    *   - client to add from group AND existing agentUsers in that group
    * @param teamMembers
    *   add to group OR existing in group
    * @param clients
    *   add to group OR existing in group
    */
  override def calculateForAddToGroup(
    groupId: GroupId,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      /* TODO: Replace pulling existing access groups with mongo query for pairs of UserEnrolments
       *   eg. maxNetChangePairs <- userEnrolmentAssignmentCalculator.pairUserEnrolments(Set[AgentUser], Set[Client])
       *       foundPairs <- accessGroupsRepository.findPairs(arn, pairs) //needs decryption mongo work?
       *       maybeUserEnrolmentAssignments <-
       *         Future.successful(userEnrolmentAssignmentCalculator.assessUserEnrolmentPairs(maxNetChangePairs, foundPairs))
       */
      existingAccessGroups <- accessGroupsRepository.get(groupId.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          userEnrolmentAssignmentCalculator.forAddToGroup(clients, teamMembers, existingAccessGroups, groupId)
        )
    } yield maybeUserEnrolmentAssignments

  override def calculateForRemoveFromGroup(
    groupId: GroupId,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      /* TODO: Replace pulling existing access groups with mongo query for pairs of UserEnrolments */
      existingAccessGroups <- accessGroupsRepository.get(groupId.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          userEnrolmentAssignmentCalculator
            .forRemoveFromGroup(clients, teamMembers, existingAccessGroups, groupId)
        )
    } yield maybeUserEnrolmentAssignments

  override def pushCalculatedAssignments(
    maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus] =
    maybeUserEnrolmentAssignments match {
      case None =>
        Future.successful(AssignmentsNotPushed)
      case Some(userEnrolmentAssignments) =>
        logger.info(
          s"Assign count: ${userEnrolmentAssignments.assign.size}, Unassign count: ${userEnrolmentAssignments.unassign.size}"
        )

        Future
          .sequence(
            UserEnrolmentAssignmentsSplitter
              .split(userEnrolmentAssignments, 500)
              .map(userClientDetailsConnector.pushAssignments(_))
          )
          .map(pushStatuses =>
            if (pushStatuses.forall(_ == AssignmentsPushed)) AssignmentsPushed else AssignmentsNotPushed
          )
    }

}
