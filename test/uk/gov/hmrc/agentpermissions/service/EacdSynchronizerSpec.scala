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
import org.scalamock.handlers.{CallHandler3, CallHandler4}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, EacdSyncRepository}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class EacdSynchronizerSpec extends BaseSpec {

  "Syncing With EACD" when {

    "synchronization happens" when {

      "removal set indicates differences between EACD and AG" should {
        "update access groups" in new TestScope {
          val accessGroup1: CustomGroup =
            buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

          val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

          (stubUserClientDetailsConnector
            .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *, *, *)
            .returns(Future.successful(Some(Seq(Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "")))))
          (stubUserClientDetailsConnector
            .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *)
            .returns(Future.successful(Seq(UserDetails(Some(agentUser1.id)))))
          (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(accessGroups))

          expectAuditClientsRemoval() // we expect an audit for the removal of clients
          // but not for the removal of team members as in this case no member should be removed

          expectAccessGroupsRepositoryUpdate(Some(1))

          eacdSynchronizer
            .syncWithEacd(arn, agentUser1)
            .futureValue shouldBe Seq(AccessGroupUpdated)
        }
      }

      "removal set indicates no differences between EACD and AG" should {
        "not update access groups" in new TestScope {
          val accessGroup1: CustomGroup =
            buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

          val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

          (stubUserClientDetailsConnector
            .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *, *, *)
            .returns(
              Future.successful(
                Some(
                  Seq(
                    Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", ""),
                    Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", ""),
                    Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "")
                  )
                )
              )
            )
          (stubUserClientDetailsConnector
            .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *)
            .returns(
              Future.successful(
                Seq(
                  UserDetails(Some(agentUser1.id))
                )
              )
            )
          (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(accessGroups))

          doNotExpectAccessGroupsRepositoryUpdate()

          eacdSynchronizer
            .syncWithEacd(
              arn,
              agentUser1
            )
            .futureValue shouldBe Seq.empty
        }
      }
    }
  }

  "Calculating user enrolments difference" when {

    "GroupDelegatedEnrolments contains mismatched enrolments than in access group" should {
      "calculate removals correctly" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt))),
          buildAccessGroup(Some(Set(agentUser2)), Some(Set(clientPpt, clientTrust)))
        )

        (stubUserClientDetailsConnector
          .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
          .when(arn, *, *, *, *)
          .returns(
            Future.successful(
              Some(
                Seq(
                  Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", ""),
                  Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "")
                )
              )
            )
          )
        (stubUserClientDetailsConnector
          .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
          .when(arn, *, *)
          .returns(
            Future.successful(
              Seq(
                UserDetails(Some(agentUser1.id)),
                UserDetails(Some("someOtherUser"))
              )
            )
          )

        val removalSet: RemovalSet = eacdSynchronizer.calculateRemovalSet(arn, accessGroups).futureValue

        removalSet.enrolmentKeysToRemove shouldBe Set(
          clientCgt.enrolmentKey,
          clientTrust.enrolmentKey
        )
        removalSet.userIdsToRemove shouldBe Set(agentUser2.id)
      }
    }
  }

  "Applying Removals On Access Groups" when {

    "removal set contains fewer enrolments than in access groups" should {
      "have corresponding enrolments and users removed" in new TestScope {
        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        val accessGroup2: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1, agentUser2)), Some(Set(clientPpt, clientTrust)))

        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1, accessGroup2)

        val removalSet: RemovalSet = RemovalSet(
          Set(clientPpt.enrolmentKey, clientTrust.enrolmentKey),
          Set(agentUser2.id)
        )

        expectAuditClientsRemoval() // we expect at least a client to be removed from group 1
        expectAuditClientsRemoval() // and also group 2
        expectAuditTeamMembersRemoval()

        val updatedGroups = eacdSynchronizer.applyRemovalSet(accessGroups, removalSet, agentUser1).futureValue

        updatedGroups(0).clients shouldBe Some(Set(clientVat, clientCgt))
        updatedGroups(0).teamMembers shouldBe Some(Set(agentUser1))
        updatedGroups(1).clients shouldBe Some(Set.empty)
        updatedGroups(1).teamMembers shouldBe Some(Set(agentUser1))
      }
    }

    "removal set contains an unknown enrolment key" should {
      "not affect existing clients of access group" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set("unknown"), Set.empty)

        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

        val updatedGroups = eacdSynchronizer.applyRemovalSet(accessGroups, removalSet, agentUser1).futureValue

        updatedGroups shouldBe accessGroups // no change
      }
    }

    "removal set contains an unknown user id" should {
      "not affect existing team members of access group" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set.empty, Set("unknown"))

        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

        val updatedGroups = eacdSynchronizer.applyRemovalSet(accessGroups, removalSet, agentUser1).futureValue

        updatedGroups shouldBe accessGroups // no change

      }
    }
  }

  "Persisting Access Groups" when {

    "update returns nothing" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        expectAccessGroupsRepositoryUpdate(None)

        eacdSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
      }
    }

    "update indicates one record was updated in DB" should {
      s"return $AccessGroupUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        expectAccessGroupsRepositoryUpdate(Some(1))

        eacdSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupUpdated)
      }
    }

    "update indicates no record was updated in DB" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        expectAccessGroupsRepositoryUpdate(Some(0))

        eacdSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName")
    val agentUser2: AgentUser = AgentUser("userId2", "userName")
    val now: LocalDateTime = LocalDateTime.now()

    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")

    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")

    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val clientMtdit: Client = Client(s"$serviceMtdit~$serviceIdentifierKeyMtdit~236216873678126", "Jane Doe")

    val clientTrust: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~0123456789", "Trust Client")

    implicit val materializer: Materializer = Materializer(ActorSystem())
    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]
    val stubUserClientDetailsConnector: UserClientDetailsConnector = stub[UserClientDetailsConnector]
    val mockAuditService: AuditService = mock[AuditService]

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val eacdSynchronizer =
      new EacdSynchronizerImpl(
        stubUserClientDetailsConnector,
        mockAccessGroupsRepository,
        mockAuditService
      )

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]]): CustomGroup =
      CustomGroup(
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

    def expectAccessGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, CustomGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: CustomGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

    def doNotExpectAccessGroupsRepositoryUpdate(): CallHandler3[Arn, String, CustomGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: CustomGroup))
        .expects(arn, groupName, *)
        .never

    def expectAuditClientsRemoval(): CallHandler4[CustomGroup, Set[Client], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupClientsRemoval(_: CustomGroup, _: Set[Client])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

    def expectAuditTeamMembersRemoval()
      : CallHandler4[CustomGroup, Set[AgentUser], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupTeamMembersRemoval(_: CustomGroup, _: Set[AgentUser])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

  }

}
