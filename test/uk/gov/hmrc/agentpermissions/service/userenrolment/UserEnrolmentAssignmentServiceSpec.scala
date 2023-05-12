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

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.model.UserEnrolmentAssignments
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2
import uk.gov.hmrc.agents.accessgroups.{AgentUser, CustomGroup}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class UserEnrolmentAssignmentServiceSpec extends BaseSpec {
  val user: AgentUser = AgentUser("userId", "userName")
  val groupName = "some group"
  val userEnrolmentAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(Set.empty, Set.empty, arn)
  val maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments] = Some(userEnrolmentAssignments)

  val accessGroup: CustomGroup =
    CustomGroup(GroupId.random(), arn, groupName, now, now, user, user, Set.empty, Set.empty)

  lazy val now: LocalDateTime = LocalDateTime.now()

  trait TestScope {
    val mockAccessGroupsRepository: CustomGroupsRepositoryV2 = mock[CustomGroupsRepositoryV2]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]

    val userEnrolmentAssignmentService =
      new UserEnrolmentAssignmentServiceImpl(
        mockAccessGroupsRepository,
        mockUserClientDetailsConnector
      )

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def mockAccessGroupsRepositoryGetAll(accessGroups: Seq[CustomGroup]): CallHandler1[Arn, Future[Seq[CustomGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future successful accessGroups)

    def mockAccessGroupsRepositoryGet(
      maybeAccessGroup: Option[CustomGroup]
    ): CallHandler2[Arn, String, Future[Option[CustomGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, *)
        .returning(Future successful maybeAccessGroup)

    def mockUserClientDetailsConnectorPushAssignments(
      pushStatus: EacdAssignmentsPushStatus
    ): CallHandler3[UserEnrolmentAssignments, HeaderCarrier, ExecutionContext, Future[EacdAssignmentsPushStatus]] =
      (mockUserClientDetailsConnector
        .pushAssignments(_: UserEnrolmentAssignments)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userEnrolmentAssignments, *, *)
        .anyNumberOfTimes()
        .returning(Future successful pushStatus)
  }

  "Calculating assignments during group creation" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq.empty)

      userEnrolmentAssignmentService
        .calculateForGroupCreation(accessGroup)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Calculating assignments during group update" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

      userEnrolmentAssignmentService
        .calculateForGroupUpdate(arn, groupName, accessGroup)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Calculating assignments during group update (add/remove)" should {
    "return calculated assignments for add to group" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

      userEnrolmentAssignmentService
        .calculateForAddToGroup(arn, groupName, accessGroup.clients, accessGroup.teamMembers)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }

    "return calculated assignments for remove from group" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

      userEnrolmentAssignmentService
        .calculateForRemoveFromGroup(arn, groupName, accessGroup.clients, accessGroup.teamMembers)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Calculating assignments during group delete" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
      mockAccessGroupsRepositoryGet(Some(accessGroup))

      userEnrolmentAssignmentService
        .calculateForGroupDeletion(arn, groupName)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Pushing assignments" when {

    "calculated assignments are nothing" should {
      s"return $AssignmentsNotPushed" in new TestScope {
        userEnrolmentAssignmentService.pushCalculatedAssignments(None).futureValue shouldBe AssignmentsNotPushed
      }
    }

    "calculated assignments are some value" should {
      "return status returned by connector" in new TestScope {
        val statusReturnedByConnector = AssignmentsPushed
        mockUserClientDetailsConnectorPushAssignments(statusReturnedByConnector)

        userEnrolmentAssignmentService
          .pushCalculatedAssignments(maybeUserEnrolmentAssignments)
          .futureValue shouldBe statusReturnedByConnector
      }
    }
  }
}
