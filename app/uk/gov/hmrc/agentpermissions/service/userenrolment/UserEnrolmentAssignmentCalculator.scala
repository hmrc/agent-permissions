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
import uk.gov.hmrc.agentmtdidentifiers.model.{CustomGroup, UserEnrolment, UserEnrolmentAssignments}

import javax.inject.Singleton

@ImplementedBy(classOf[UserEnrolmentAssignmentCalculatorImpl])
trait UserEnrolmentAssignmentCalculator {

  def forGroupCreation(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments]

  def forGroupUpdate(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments]

  def forGroupDeletion(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments]
}

@Singleton
class UserEnrolmentAssignmentCalculatorImpl extends UserEnrolmentAssignmentCalculator with Logging {

  override def forGroupCreation(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] = {

    val seedAssigns = explodeUserEnrolments(accessGroupToProcess)

    val seedUnassigns = Set.empty[UserEnrolment]

    Option(optimiseUserEnrolmentAssignments(accessGroupToProcess, existingAccessGroups, seedAssigns, seedUnassigns))
  }

  override def forGroupUpdate(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] =
    existingAccessGroups.find(_.groupName.equalsIgnoreCase(accessGroupToProcess.groupName)) map {
      accessGroupToProcessPreviousVersion =>
        val seedAssigns =
          explodeUserEnrolments(accessGroupToProcess) -- explodeUserEnrolments(accessGroupToProcessPreviousVersion)

        val seedUnassigns =
          explodeUserEnrolments(accessGroupToProcessPreviousVersion) -- explodeUserEnrolments(accessGroupToProcess)

        optimiseUserEnrolmentAssignments(accessGroupToProcess, existingAccessGroups, seedAssigns, seedUnassigns)
    }

  override def forGroupDeletion(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup]
  ): Option[UserEnrolmentAssignments] = {

    val seedAssigns = Set.empty[UserEnrolment]

    val seedUnassigns = explodeUserEnrolments(accessGroupToProcess)

    Option(optimiseUserEnrolmentAssignments(accessGroupToProcess, existingAccessGroups, seedAssigns, seedUnassigns))
  }

  private def explodeUserEnrolments(accessGroup: CustomGroup): Set[UserEnrolment] = for {
    userId       <- accessGroup.teamMembers.toSet.flatten.map(_.id);
    enrolmentKey <- accessGroup.clients.toSet.flatten.map(_.enrolmentKey)
  } yield UserEnrolment(userId, enrolmentKey)

  private def optimiseUserEnrolmentAssignments(
    accessGroupToProcess: CustomGroup,
    existingAccessGroups: Seq[CustomGroup],
    seedAssigns: Set[UserEnrolment],
    seedUnassigns: Set[UserEnrolment]
  ): UserEnrolmentAssignments =
    existingAccessGroups
      .filterNot(_.groupName.equalsIgnoreCase(accessGroupToProcess.groupName))
      .foldLeft(UserEnrolmentAssignments(seedAssigns, seedUnassigns, accessGroupToProcess.arn)) {
        (userEnrolmentAssignments, existingAccessGroup) =>
          val userEnrolments = explodeUserEnrolments(existingAccessGroup)

          userEnrolmentAssignments.copy(
            assign = userEnrolmentAssignments.assign -- userEnrolments,
            unassign = userEnrolmentAssignments.unassign -- userEnrolments
          )
      }

}
