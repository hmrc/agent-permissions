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

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, GroupId, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class UserEnrolmentAssignmentServiceSpec extends BaseSpec {

  val arn: Arn = Arn("KARN1234567")
  val user: AgentUser = AgentUser("userId", "userName")
  val groupName = "some group"
  val groupId: GroupId = GroupId(arn, groupName)
  val userEnrolmentAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(Set.empty, Set.empty)
  val maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments] = Some(userEnrolmentAssignments)

  val accessGroup: AccessGroup = AccessGroup(arn, groupName, now, now, user, user, Some(Set.empty), Some(Set.empty))

  lazy val now: LocalDateTime = LocalDateTime.now()

  trait TestScope {
    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]
    val mockUserEnrolmentAssignmentCalculator: UserEnrolmentAssignmentCalculator =
      mock[UserEnrolmentAssignmentCalculator]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]

    val userEnrolmentAssignmentService =
      new UserEnrolmentAssignmentServiceImpl(
        mockAccessGroupsRepository,
        mockUserEnrolmentAssignmentCalculator,
        mockUserClientDetailsConnector
      )

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def mockAccessGroupsRepositoryGetAll(accessGroups: Seq[AccessGroup]): CallHandler1[Arn, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future successful accessGroups)

    def mockAccessGroupsRepositoryGet(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler2[Arn, String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, *)
        .returning(Future successful maybeAccessGroup)

    def mockUserEnrolmentAssignmentCalculatorForGroupCreation(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[AccessGroup, Seq[AccessGroup], Option[UserEnrolmentAssignments]] =
      (mockUserEnrolmentAssignmentCalculator
        .forGroupCreation(_: AccessGroup, _: Seq[AccessGroup]))
        .expects(accessGroup, *)
        .returning(maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentCalculatorForGroupUpdate(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[AccessGroup, Seq[AccessGroup], Option[UserEnrolmentAssignments]] =
      (mockUserEnrolmentAssignmentCalculator
        .forGroupUpdate(_: AccessGroup, _: Seq[AccessGroup]))
        .expects(accessGroup, *)
        .returning(maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentCalculatorForGroupDeletion(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[AccessGroup, Seq[AccessGroup], Option[UserEnrolmentAssignments]] =
      (mockUserEnrolmentAssignmentCalculator
        .forGroupDeletion(_: AccessGroup, _: Seq[AccessGroup]))
        .expects(accessGroup, *)
        .returning(maybeUserEnrolmentAssignments)

    def mockUserClientDetailsConnectorPushAssignments(
      pushStatus: EacdAssignmentsPushStatus
    ): CallHandler3[UserEnrolmentAssignments, HeaderCarrier, ExecutionContext, Future[EacdAssignmentsPushStatus]] =
      (mockUserClientDetailsConnector
        .pushAssignments(_: UserEnrolmentAssignments)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userEnrolmentAssignments, *, *)
        .returning(Future successful pushStatus)
  }

  "Calculating assignments during group creation" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq.empty)
      mockUserEnrolmentAssignmentCalculatorForGroupCreation(maybeUserEnrolmentAssignments)

      userEnrolmentAssignmentService
        .calculateForGroupCreation(accessGroup)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Calculating assignments during group update" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
      mockUserEnrolmentAssignmentCalculatorForGroupUpdate(maybeUserEnrolmentAssignments)

      userEnrolmentAssignmentService
        .calculateForGroupUpdate(groupId, accessGroup)
        .futureValue shouldBe maybeUserEnrolmentAssignments
    }
  }

  "Calculating assignments during group delete" should {
    "return calculated assignments" in new TestScope {
      mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
      mockAccessGroupsRepositoryGet(Some(accessGroup))
      mockUserEnrolmentAssignmentCalculatorForGroupDeletion(maybeUserEnrolmentAssignments)

      userEnrolmentAssignmentService
        .calculateForGroupDeletion(groupId)
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
