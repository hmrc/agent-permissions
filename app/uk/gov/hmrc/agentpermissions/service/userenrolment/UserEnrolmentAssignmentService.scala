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
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, GroupId, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserEnrolmentAssignmentServiceImpl])
trait UserEnrolmentAssignmentService {

  def calculateForGroupCreation(accessGroup: AccessGroup)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupDeletion(groupId: GroupId)(implicit
    ec: ExecutionContext
  ): Future[Option[UserEnrolmentAssignments]]

  def calculateForGroupUpdate(
    groupId: GroupId,
    accessGroupToUpdate: AccessGroup
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
    accessGroup: AccessGroup
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

  override def calculateForGroupUpdate(
    groupId: GroupId,
    accessGroupToUpdate: AccessGroup
  )(implicit ec: ExecutionContext): Future[Option[UserEnrolmentAssignments]] =
    for {
      existingAccessGroups <- accessGroupsRepository.get(groupId.arn)
      maybeUserEnrolmentAssignments <-
        Future.successful(userEnrolmentAssignmentCalculator.forGroupUpdate(accessGroupToUpdate, existingAccessGroups))
    } yield maybeUserEnrolmentAssignments

  override def pushCalculatedAssignments(
    maybeAssignments: Option[UserEnrolmentAssignments]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[EacdAssignmentsPushStatus] =
    maybeAssignments match {
      case None =>
        Future.successful(AssignmentsNotPushed)
      case Some(assignments) =>
        logger.debug(s"EACD assignments calculated: ${Json.toJson(assignments)}")
        logger.info(s"Assign count: ${assignments.assign.size}, Unassign count: ${assignments.unassign.size}")

        userClientDetailsConnector.pushAssignments(assignments)
    }

}
