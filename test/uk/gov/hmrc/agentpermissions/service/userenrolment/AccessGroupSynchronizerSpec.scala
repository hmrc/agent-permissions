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

import org.scalamock.handlers.{CallHandler1, CallHandler3, CallHandler5}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.{AccessGroupNotUpdated, AccessGroupUpdated}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AccessGroupSynchronizerSpec extends BaseSpec {

  "Syncing With EACD" when {

    "synchronization happens successfully" should {
      "indicate access groups were updated" in new TestScope {
        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

        mockAccessGroupsRepositoryGetAll(accessGroups)
        mockRemoveClientsFromGroup(accessGroup1, Set(clientPpt, clientCgt).map(_.enrolmentKey), agentUser1)
        mockRemoveTeamMembersFromGroup(accessGroup1, Set.empty, agentUser1)
        mockAccessGroupsRepositoryUpdate(Some(1))

        accessGroupSynchronizer
          .syncWithEacd(
            arn,
            GroupDelegatedEnrolments(
              Seq(
                AssignedClient(s"$serviceVat~$serviceIdentifierKeyVat~101747641", None, agentUser1.id)
              )
            ),
            agentUser1
          )
          .futureValue shouldBe Seq(AccessGroupUpdated)
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

        val groupDelegatedEnrolments: GroupDelegatedEnrolments = GroupDelegatedEnrolments(
          Seq(
            AssignedClient(s"$serviceVat~$serviceIdentifierKeyVat~101747641", None, agentUser1.id),
            AssignedClient(
              s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345",
              None,
              "someOtherUser"
            )
          )
        )
        val removalSet: RemovalSet = accessGroupSynchronizer.calculateRemovalSet(accessGroups, groupDelegatedEnrolments)

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

        mockRemoveClientsFromGroup(accessGroup1, Set(clientPpt, clientTrust).map(_.enrolmentKey), agentUser1)
        mockRemoveTeamMembersFromGroup(accessGroup1, Set(agentUser2.id), agentUser1)
        mockRemoveClientsFromGroup(accessGroup2, Set(clientPpt, clientTrust).map(_.enrolmentKey), agentUser1)
        mockRemoveTeamMembersFromGroup(accessGroup2, Set(agentUser2.id), agentUser1)

        accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue

      }
    }

    "removal set contains an unknown enrolment key" should {
      "not affect existing clients of access group" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set("unknown"), Set.empty)

        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

        mockRemoveClientsFromGroup(accessGroup1, Set("unknown"), agentUser1)
        mockRemoveTeamMembersFromGroup(accessGroup1, Set.empty, agentUser1)

        accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue
      }
    }

    "removal set contains an unknown user id" should {
      "not affect existing team members of access group" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set.empty, Set("unknown"))

        val accessGroup1: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

        mockRemoveClientsFromGroup(accessGroup1, Set.empty, agentUser1)
        mockRemoveTeamMembersFromGroup(accessGroup1, Set("unknown"), agentUser1)

        accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue
      }
    }
  }

  "Persisting Access Groups" when {

    "update returns nothing" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        mockAccessGroupsRepositoryUpdate(None)

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
      }
    }

    "update indicates one record was updated in DB" should {
      s"return $AccessGroupUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        mockAccessGroupsRepositoryUpdate(Some(1))

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupUpdated)
      }
    }

    "update indicates no record was updated in DB" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[CustomGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))
        )

        mockAccessGroupsRepositoryUpdate(Some(0))

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
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

    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]
    val mockGroupClientsRemover: GroupClientsRemover = mock[GroupClientsRemover]
    val mockGroupTeamMembersRemover: GroupTeamMembersRemover = mock[GroupTeamMembersRemover]

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val accessGroupSynchronizer =
      new AccessGroupSynchronizerImpl(mockAccessGroupsRepository, mockGroupClientsRemover, mockGroupTeamMembersRemover)

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

    def mockAccessGroupsRepositoryGetAll(
      accessGroups: Seq[CustomGroup]
    ): CallHandler1[Arn, Future[Seq[CustomGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockAccessGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, CustomGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: CustomGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

    def mockRemoveClientsFromGroup(
      accessGroup: CustomGroup,
      removalEnrolmentKeys: Set[String],
      whoIsUpdating: AgentUser
    ): CallHandler5[CustomGroup, Set[String], AgentUser, HeaderCarrier, ExecutionContext, CustomGroup] =
      (mockGroupClientsRemover
        .removeClientsFromGroup(_: CustomGroup, _: Set[String], _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(accessGroup, removalEnrolmentKeys, whoIsUpdating, *, *)
        .returning(accessGroup)

    def mockRemoveTeamMembersFromGroup(
      accessGroup: CustomGroup,
      removalUserIds: Set[String],
      whoIsUpdating: AgentUser
    ): CallHandler5[CustomGroup, Set[String], AgentUser, HeaderCarrier, ExecutionContext, CustomGroup] =
      (mockGroupTeamMembersRemover
        .removeTeamMembersFromGroup(_: CustomGroup, _: Set[String], _: AgentUser)(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(accessGroup, removalUserIds, whoIsUpdating, *, *)
        .returning(accessGroup)
  }

}
