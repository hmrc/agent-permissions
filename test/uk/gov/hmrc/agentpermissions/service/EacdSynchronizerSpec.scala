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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.handlers.{CallHandler0, CallHandler1, CallHandler3, CallHandler4}
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, EacdSyncRecord, EacdSyncRepository, TaxGroupsRepositoryV2}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.model.accessgroups._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class EacdSynchronizerSpec extends BaseSpec {

  "Sync control" should {
    val user: AgentUser = AgentUser("userId", "userName")

    "not sync if outstanding work items cannot be determined" in new TestScope {
      outstandingAssignmentsWorkItemsExist(None)
      eacdSynchronizer.syncWithEacd(arn).futureValue shouldBe None
    }

    "not sync if outstanding assignment work items exist" in new TestScope {
      outstandingAssignmentsWorkItemsExist(Some(true))
      eacdSynchronizer.syncWithEacd(arn).futureValue shouldBe None
    }

    "not sync if there are no outstanding assignment work items but EACD sync token is not acquired" in new TestScope {
      outstandingAssignmentsWorkItemsExist(Some(false))
      appConfigEacdSetSeconds(10)
      syncRepoCannotBeAcquired()
      eacdSynchronizer.syncWithEacd(arn).futureValue shouldBe None
    }

    "not sync if there are no outstanding assignment work items, EACD sync token is acquired but there are no access groups" in new TestScope {
      outstandingAssignmentsWorkItemsExist(Some(false))
      appConfigEacdSetSeconds(10)
      syncRepoCanBeAcquired()
      (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(Seq.empty))
      (stubTaxServiceGroupsRepository.get(_: Arn)).when(arn).returns(Future.successful(Seq.empty))

      eacdSynchronizer.syncWithEacd(arn).futureValue shouldBe Some(Map.empty)
    }

    "do the sync if all conditions are satisfied" in new TestScope {
      outstandingAssignmentsWorkItemsExist(Some(false))
      appConfigEacdSetSeconds(10)
      syncRepoCanBeAcquired()

      val customGroups: Seq[CustomGroup] = Seq(buildCustomGroup(Set(user), Set(clientVat, clientPpt, clientCgt)))
      (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(customGroups))
      (stubTaxServiceGroupsRepository.get(_: Arn)).when(arn).returns(Future.successful(Seq.empty))
      (stubUserClientDetailsConnector
        .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .when(arn, *, *, *, *)
        .returns(Future.successful(Some(Seq(Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "")))))
      (stubUserClientDetailsConnector
        .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(arn, *, *)
        .returns(Future.successful(Seq(UserDetails(Some(agentUser1.id)))))

      expectAuditClientsRemoval()
      expectAuditTeamMembersRemoval()
      expectAccessGroupsRepositoryUpdate(Some(1))

      eacdSynchronizer.syncWithEacd(arn).futureValue shouldBe Some(Map(SyncResult.AccessGroupUpdateSuccess -> 1))
    }
  }

  "Syncing With EACD" when {

    "synchronization happens" when {

      "removal set indicates differences between EACD and AG" should {
        "update access groups" in new TestScope {
          val accessGroup1: CustomGroup =
            buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))

          val accessGroups: Seq[CustomGroup] = Seq(accessGroup1)

          (stubUserClientDetailsConnector
            .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *, *, *)
            .returns(Future.successful(Some(Seq(Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "")))))
          (stubUserClientDetailsConnector
            .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
            .when(arn, *, *)
            .returns(Future.successful(Seq(UserDetails(Some(agentUser1.id)))))
          (stubTaxServiceGroupsRepository.get(_: Arn)).when(arn).returns(Future.successful(Seq.empty))
          (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(accessGroups))
          appConfigEacdSetSeconds(10)
          outstandingAssignmentsWorkItemsExist(Some(false))
          syncRepoCanBeAcquired()

          expectAuditClientsRemoval() // we expect an audit for the removal of clients
          // but not for the removal of team members as in this case no member should be removed

          expectAccessGroupsRepositoryUpdate(Some(1))

          eacdSynchronizer
            .syncWithEacd(arn)
            .futureValue shouldBe Some(Map(SyncResult.AccessGroupUpdateSuccess -> 1))
        }
      }

      "removal set indicates no differences between EACD and AG" should {
        "not update access groups" in new TestScope {
          val accessGroup1: CustomGroup =
            buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))

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
          (stubTaxServiceGroupsRepository.get(_: Arn)).when(arn).returns(Future.successful(Seq.empty))
          (mockAccessGroupsRepository.get(_: Arn)).expects(arn).returning(Future.successful(accessGroups))
          appConfigEacdSetSeconds(10)
          outstandingAssignmentsWorkItemsExist(Some(false))
          syncRepoCanBeAcquired()

          doNotExpectAccessGroupsRepositoryUpdate()

          eacdSynchronizer
            .syncWithEacd(arn)
            .futureValue shouldBe Some(Map(SyncResult.AccessGroupUnchanged -> 1))
        }
      }
    }
  }

  "Calculating user enrolments difference" when {

    "GroupDelegatedEnrolments contains mismatched enrolments than in access group" should {
      "calculate removals correctly" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt)),
          buildTaxGroup("HMRC-MTD-VAT", Set(agentUser2), Set(clientPpt, clientTrust))
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
      "have corresponding enrolments and users removed (custom group)" in new TestScope {
        val accessGroup: CustomGroup =
          buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt, clientTrust))

        val removalSet: RemovalSet = RemovalSet(
          Set(clientPpt.enrolmentKey, clientTrust.enrolmentKey),
          Set(agentUser2.id)
        )

        expectAuditClientsRemoval()
        expectAuditTeamMembersRemoval()

        val updatedGroup = eacdSynchronizer.applyRemovalSet(accessGroup, removalSet, agentUser1).futureValue

        updatedGroup should matchPattern {
          case cg: CustomGroup if cg.clients == Set(clientVat, clientCgt) && cg.teamMembers == Set(agentUser1) =>
        }
      }

      "have corresponding enrolments and users removed (tax group)" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(
          Set(clientPpt.enrolmentKey, clientTrust.enrolmentKey),
          Set(agentUser2.id)
        )

        expectAuditExcludedClientsRemoval()
        expectAuditTeamMembersRemoval()

        val accessGroup: TaxGroup =
          buildTaxGroup("HMRC-MTD-VAT", Set(agentUser1, agentUser2), Set(clientVat, clientPpt, clientCgt, clientTrust))
        eacdSynchronizer.applyRemovalSet(accessGroup, removalSet, agentUser1).futureValue should matchPattern {
          case tg: TaxGroup if tg.teamMembers == Set(agentUser1) && tg.excludedClients == Set(clientVat, clientCgt) =>
        }
      }
    }

    "removal set contains an unknown enrolment key" should {
      "not affect existing clients of access group (both custom and tax groups)" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set("unknown"), Set.empty)

        val customGroup = buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))
        eacdSynchronizer
          .applyRemovalSet(customGroup, removalSet, agentUser1)
          .futureValue shouldBe customGroup // no change

        val taxGroup = buildTaxGroup("HMRC-MTD-VAT", Set(agentUser1), Set(clientVat))
        eacdSynchronizer.applyRemovalSet(taxGroup, removalSet, agentUser1).futureValue shouldBe taxGroup // no change
      }
    }

    "removal set contains an unknown user id" should {
      "not affect existing team members of access group (both custom and tax groups)" in new TestScope {
        val removalSet: RemovalSet = RemovalSet(Set.empty, Set("unknown"))

        val customGroup = buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))
        eacdSynchronizer
          .applyRemovalSet(customGroup, removalSet, agentUser1)
          .futureValue shouldBe customGroup // no change

        val taxGroup = buildTaxGroup("HMRC-MTD-VAT", Set(agentUser1), Set(clientVat))
        eacdSynchronizer.applyRemovalSet(taxGroup, removalSet, agentUser1).futureValue shouldBe taxGroup // no change
      }
    }
  }

  "Persisting Access Groups" when {

    "update returns nothing" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroup: AccessGroup =
          buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))

        expectAccessGroupsRepositoryUpdate(None)

        eacdSynchronizer.persistAccessGroup(accessGroup).futureValue shouldBe SyncResult.AccessGroupUpdateFailure
      }
    }

    "update indicates one record (a custom group) was updated in DB" should {
      s"return $AccessGroupUpdated" in new TestScope {
        val accessGroup: AccessGroup =
          buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))

        expectAccessGroupsRepositoryUpdate(Some(1))

        eacdSynchronizer.persistAccessGroup(accessGroup).futureValue shouldBe SyncResult.AccessGroupUpdateSuccess
      }
    }

    "update indicates no record was updated in DB" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroup: AccessGroup =
          buildCustomGroup(Set(agentUser1), Set(clientVat, clientPpt, clientCgt))

        expectAccessGroupsRepositoryUpdate(Some(0))

        eacdSynchronizer.persistAccessGroup(accessGroup).futureValue shouldBe SyncResult.AccessGroupUpdateFailure
      }
    }

    "update indicates one record (a tax group) was updated in DB" should {
      s"return $AccessGroupUpdated" in new TestScope {
        val accessGroup: AccessGroup =
          buildTaxGroup("HMRC-MTD-VAT", Set(agentUser1), Set.empty)

        expectTaxGroupsRepositoryUpdate(Some(1))

        eacdSynchronizer.persistAccessGroup(accessGroup).futureValue shouldBe SyncResult.AccessGroupUpdateSuccess
      }
    }

    "'full sync'" should {
      "call AUCD once for each member" in new TestScope {
        (stubUserClientDetailsConnector
          .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
          .when(arn, *, *)
          .returns(
            Future.successful(
              Seq(
                UserDetails(Some(agentUser1.id)),
                UserDetails(Some(agentUser2.id)),
                UserDetails(Some("aThirdUserNotInAnyGroup"))
              )
            )
          )
        (stubUserClientDetailsConnector
          .syncTeamMember(_: Arn, _: String, _: Seq[String])(_: HeaderCarrier, _: ExecutionContext))
          .when(*, *, *, *, *)
          .returns(Future.successful(true))

        eacdSynchronizer
          .doFullSync(
            arn,
            Seq(
              buildCustomGroup(Set(agentUser1, agentUser2), Set(clientVat)),
              buildCustomGroup(Set(agentUser2), Set(clientCgt))
            )
          )
          .futureValue

        (stubUserClientDetailsConnector
          .syncTeamMember(_: Arn, _: String, _: Seq[String])(_: HeaderCarrier, _: ExecutionContext))
          .verify(arn, agentUser1.id, Seq(clientVat.enrolmentKey), *, *)
          .once()
        (stubUserClientDetailsConnector
          .syncTeamMember(_: Arn, _: String, _: Seq[String])(_: HeaderCarrier, _: ExecutionContext))
          .verify(arn, agentUser2.id, Seq(clientVat.enrolmentKey, clientCgt.enrolmentKey), *, *)
          .once()
        (stubUserClientDetailsConnector
          .syncTeamMember(_: Arn, _: String, _: Seq[String])(_: HeaderCarrier, _: ExecutionContext))
          .verify(arn, "aThirdUserNotInAnyGroup", Seq.empty, *, *)
          .once()
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
    val clientCbc: Client = Client(s"$serviceCbc~$serviceIdentifierKeyCbc~236216873678126", "Colm Doe")
    val clientTrust: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~0123456789", "Trust Client")

    implicit val materializer: Materializer = Materializer(ActorSystem())
    val mockAccessGroupsRepository: CustomGroupsRepositoryV2 = mock[CustomGroupsRepositoryV2]
    val stubUserClientDetailsConnector: UserClientDetailsConnector = stub[UserClientDetailsConnector]
    val mockAuditService: AuditService = mock[AuditService]
    val stubEacdSyncRepository: EacdSyncRepository = stub[EacdSyncRepository]
    val stubTaxServiceGroupsRepository: TaxGroupsRepositoryV2 = stub[TaxGroupsRepositoryV2]
    val stubAppConfig: AppConfig = stub[AppConfig]
    val actorSystem: ActorSystem = ActorSystem("test")

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val eacdSynchronizer =
      new EacdSynchronizerImpl(
        stubUserClientDetailsConnector,
        mockAccessGroupsRepository,
        stubTaxServiceGroupsRepository,
        stubEacdSyncRepository,
        mockAuditService,
        actorSystem,
        stubAppConfig
      )

    def buildCustomGroup(teamMembers: Set[AgentUser], clients: Set[Client]): CustomGroup =
      CustomGroup(
        GroupId.random(),
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

    def buildTaxGroup(service: String, teamMembers: Set[AgentUser], excludedClients: Set[Client]): TaxGroup =
      TaxGroup(
        GroupId.random(),
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        service,
        false,
        excludedClients
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
        .never()

    def expectTaxGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, TaxGroup, Future[Option[Long]]] =
      (stubTaxServiceGroupsRepository
        .update(_: Arn, _: String, _: TaxGroup))
        .when(arn, groupName, *)
        .returns(Future.successful(maybeModifiedCount))

    def expectAuditClientsRemoval(): CallHandler4[CustomGroup, Set[Client], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupClientsRemoval(_: CustomGroup, _: Set[Client])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

    def expectAuditExcludedClientsRemoval()
      : CallHandler4[TaxGroup, Set[Client], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupExcludedClientsRemoval(_: TaxGroup, _: Set[Client])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

    def expectAuditTeamMembersRemoval()
      : CallHandler4[CustomGroup, Set[AgentUser], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupTeamMembersRemoval(_: CustomGroup, _: Set[AgentUser])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

    def appConfigEacdSetSeconds(notBeforeSeconds: Int): CallHandler0[Int] =
      (() => stubAppConfig.eacdSyncNotBeforeSeconds).when().returns(notBeforeSeconds)

    def outstandingAssignmentsWorkItemsExist(
      itemsExist: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (stubUserClientDetailsConnector
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .when(arn, *, *)
        .returns(Future.successful(itemsExist))

    def syncRepoCanBeAcquired(): CallHandler1[Arn, Future[Option[EacdSyncRecord]]]#Derived = (stubEacdSyncRepository
      .acquire(_: Arn))
      .when(arn)
      .returns(Future.successful(Some(EacdSyncRecord(arn, Instant.now()))))

    def syncRepoCannotBeAcquired(): CallHandler1[Arn, Future[Option[EacdSyncRecord]]]#Derived =
      (stubEacdSyncRepository
        .acquire(_: Arn))
        .when(arn)
        .returns(Future.successful(None))
  }

}
