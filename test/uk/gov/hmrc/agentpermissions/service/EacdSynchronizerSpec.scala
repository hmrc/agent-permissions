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

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.scalamock.handlers.{CallHandler0, CallHandler2, CallHandler3, CallHandler4, CallHandler5}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{EacdSyncRecord, EacdSyncRepository}
import uk.gov.hmrc.agentpermissions.service.userenrolment.AccessGroupSynchronizer
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class EacdSynchronizerSpec extends BaseSpec {

  "Syncing with EACD" when {

    "fetching outstanding assignment work items returns nothing" should {
      "not attempt to sync with EACD" in new TestScope {
        mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(None)

        eacdSynchronizer.syncWithEacd(arn, user).futureValue shouldBe Seq.empty
      }
    }

    "fetching outstanding assignment work items returns a value" when {

      "outstanding assignment work items exist" should {
        "not attempt to sync with EACD" in new TestScope {
          mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(Some(true))

          eacdSynchronizer.syncWithEacd(arn, user).futureValue shouldBe Seq.empty
        }
      }

      "outstanding assignment work items do not exist" when {

        "eacd sync token is not acquired" should {
          "not attempt to sync with EACD" in new TestScope {
            mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(Some(false))

            mockAppConfigEacdSyncNotBeforeSeconds(10)
            mockEacdSyncRepositoryAcquire(Option.empty[EacdSyncRecord])

            eacdSynchronizer.syncWithEacd(arn, user).futureValue shouldBe Seq.empty
          }
        }

        "eacd sync token is acquired" when {

          "call to fetch assigned users returns nothing" should {
            "not attempt to sync with EACD" in new TestScope {
              mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(Some(false))

              mockAppConfigEacdSyncNotBeforeSeconds(10)
              mockEacdSyncRepositoryAcquire(Option(EacdSyncRecord(arn, Instant.now())))

              mockUserClientDetailsConnectorGetClientsWithAssignedUsers(None)

              eacdSynchronizer.syncWithEacd(arn, user).futureValue shouldBe Seq.empty
            }
          }

          "call to fetch assigned users returns a value" should {
            "attempt to sync with EACD" in new TestScope {
              mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(Some(false))

              mockAppConfigEacdSyncNotBeforeSeconds(10)
              mockEacdSyncRepositoryAcquire(Option(EacdSyncRecord(arn, Instant.now())))

              mockUserClientDetailsConnectorGetClientsWithAssignedUsers(
                Some(GroupDelegatedEnrolments(Seq(assignedClient)))
              )

              mockAccessGroupSynchronizerSyncWithEacd(Seq(AccessGroupUpdated))

              eacdSynchronizer.syncWithEacd(arn, user).futureValue shouldBe Seq(AccessGroupUpdated)
            }
          }

        }
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")
    val clients = Seq(clientVat, clientPpt, clientCgt)

    val assignedClient: AssignedClient = AssignedClient("service~key~value", None, "user")

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val materializer: Materializer = Materializer(ActorSystem())
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockAccessGroupSynchronizer: AccessGroupSynchronizer = mock[AccessGroupSynchronizer]
    val mockEacdSyncRepository: EacdSyncRepository = mock[EacdSyncRepository]
    val mockAppConfig: AppConfig = mock[AppConfig]

    val eacdSynchronizer: EacdSynchronizer =
      new EacdSynchronizerImpl(
        mockUserClientDetailsConnector,
        mockAccessGroupSynchronizer,
        mockEacdSyncRepository,
        mockAppConfig
      )

    val userEnrolmentAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(
      assign = Set(UserEnrolment(user.id, clientVat.enrolmentKey)),
      unassign = Set.empty,
      arn = arn
    )
    val maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments] = Some(userEnrolmentAssignments)

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockUserClientDetailsConnectorGetClientsWithAssignedUsers(
      maybeGroupDelegatedEnrolments: Option[GroupDelegatedEnrolments]
    ): CallHandler4[Arn, HeaderCarrier, ExecutionContext, Materializer, Future[Option[GroupDelegatedEnrolments]]] =
      (mockUserClientDetailsConnector
        .getClientsWithAssignedUsers(_: Arn)(_: HeaderCarrier, _: ExecutionContext, _: Materializer))
        .expects(arn, *, *, *)
        .returning(Future successful maybeGroupDelegatedEnrolments)

    def mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(
      maybeOutstandingAssignmentsWorkItemsExist: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful maybeOutstandingAssignmentsWorkItemsExist)

    def mockAccessGroupSynchronizerSyncWithEacd(
      accessGroupUpdateStatuses: Seq[AccessGroupUpdateStatus]
    ): CallHandler5[Arn, GroupDelegatedEnrolments, AgentUser, HeaderCarrier, ExecutionContext, Future[
      Seq[AccessGroupUpdateStatus]
    ]] =
      (mockAccessGroupSynchronizer
        .syncWithEacd(_: Arn, _: GroupDelegatedEnrolments, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *, *, *)
        .returning(Future successful accessGroupUpdateStatuses)

    def mockAppConfigEacdSyncNotBeforeSeconds(notBeforeSeconds: Int): CallHandler0[Int] =
      (() => mockAppConfig.eacdSyncNotBeforeSeconds).expects().returning(notBeforeSeconds)

    def mockEacdSyncRepositoryAcquire(
      maybeEacdSyncRecord: Option[EacdSyncRecord]
    ): CallHandler2[Arn, Int, Future[Option[EacdSyncRecord]]] = (mockEacdSyncRepository
      .acquire(_: Arn, _: Int))
      .expects(arn, *)
      .returning(Future successful maybeEacdSyncRecord)

  }

}
