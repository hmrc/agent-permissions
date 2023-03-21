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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.model.UserEnrolmentAssignments
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserEnrolmentAssignmentServiceImpl])
trait UserEnrolmentAssignmentService {

  def calculateForGroupCreation(accessGroup: CustomGroup)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupDeletion(arn: Arn, groupName: String)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupUpdate(arn: Arn, groupName: String, accessGroupToUpdate: CustomGroup)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForAddToGroup(
    arn: Arn,
    groupName: String,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]]

  def calculateForRemoveFromGroup(
    arn: Arn,
    groupName: String,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]]

  def pushCalculatedAssignments(
    maybeCalculatedAssignments: Option[UserEnrolmentAssignments]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus]
}

@Singleton
class UserEnrolmentAssignmentServiceImpl @Inject() (
  customGroupsRepository: CustomGroupsRepositoryV2,
  userClientDetailsConnector: UserClientDetailsConnector
) extends UserEnrolmentAssignmentService with Logging {

  override def calculateForGroupCreation(
    accessGroup: CustomGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups <- customGroupsRepository.get(accessGroup.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(UserEnrolmentAssignmentOps.forGroupCreation(accessGroup, existingAccessGroups))
    } yield maybeUserEnrolmentAssignments

  override def calculateForGroupDeletion(arn: Arn, groupName: String)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups     <- customGroupsRepository.get(arn)
      maybeExistingAccessGroup <- customGroupsRepository.get(arn, groupName)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          maybeExistingAccessGroup.flatMap(
            UserEnrolmentAssignmentOps.forGroupDeletion(_, existingAccessGroups)
          )
        )
    } yield maybeUserEnrolmentAssignments

  override def calculateForGroupUpdate(
    arn: Arn,
    groupName: String,
    accessGroupToUpdate: CustomGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups <- customGroupsRepository.get(arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          UserEnrolmentAssignmentOps.forGroupUpdate(accessGroupToUpdate, existingAccessGroups)
        )
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
    arn: Arn,
    groupName: String,
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
      existingAccessGroups <- customGroupsRepository.get(arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          UserEnrolmentAssignmentOps.forAddToGroup(clients, teamMembers, existingAccessGroups, arn, groupName)
        )
    } yield maybeUserEnrolmentAssignments

  override def calculateForRemoveFromGroup(
    arn: Arn,
    groupName: String,
    clients: Set[Client],
    teamMembers: Set[AgentUser]
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      /* TODO: Replace pulling existing access groups with mongo query for pairs of UserEnrolments */
      existingAccessGroups <- customGroupsRepository.get(arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(
          UserEnrolmentAssignmentOps
            .forRemoveFromGroup(clients, teamMembers, existingAccessGroups, arn, groupName)
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
            UserEnrolmentAssignmentOps
              .split(userEnrolmentAssignments, 500)
              .map(userClientDetailsConnector.pushAssignments(_))
          )
          .map(pushStatuses =>
            if (pushStatuses.forall(_ == AssignmentsPushed)) AssignmentsPushed else AssignmentsNotPushed
          )
    }

}
