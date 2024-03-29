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

import com.mongodb.client.result.UpdateResult
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.scalamock.handlers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, AssignedClient, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.model.{UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentService
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class CustomGroupsServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val groupName = "some group"
    val insertedId = "insertedId"
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val user3: AgentUser = AgentUser("user3", "User 3")
    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val dbId: GroupId = GroupId.random()

    val accessGroup: CustomGroup = CustomGroup(
      GroupId.random(),
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Set(user, user1, user2),
      Set(clientVat, clientPpt, clientCgt)
    )

    val taxServiceGroup: TaxGroup =
      TaxGroup(
        GroupId.random(),
        arn,
        groupName,
        now,
        now,
        user,
        user,
        Set.empty,
        serviceVat,
        automaticUpdates = true,
        Set.empty
      )

    val clients = Seq(clientVat, clientPpt, clientCgt)

    val accessGroupInMongo: CustomGroup = withClientNamesRemoved(accessGroup)

    val assignedClient: AssignedClient = AssignedClient("service~key~value", None, "user")

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockAccessGroupsRepository: CustomGroupsRepositoryV2 = mock[CustomGroupsRepositoryV2]
    val mockUserEnrolmentAssignmentService: UserEnrolmentAssignmentService = mock[UserEnrolmentAssignmentService]
    val mockTaxGroupsService: TaxGroupsService = mock[TaxGroupsService]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockAuditService: AuditService = mock[AuditService]

    val accessGroupsService: CustomGroupsService =
      new CustomGroupsServiceImpl(
        mockAccessGroupsRepository,
        mockUserEnrolmentAssignmentService,
        mockTaxGroupsService,
        mockUserClientDetailsConnector,
        mockAuditService
      )

    val userEnrolmentAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(
      assign = Set(UserEnrolment(user.id, clientVat.enrolmentKey)),
      unassign = Set.empty,
      arn = arn
    )
    val maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments] = Some(userEnrolmentAssignments)

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockAccessGroupsRepositoryGet(
      maybeAccessGroup: Option[CustomGroup]
    ): CallHandler2[Arn, String, Future[Option[CustomGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryFindById(
      maybeAccessGroup: Option[CustomGroup]
    ): CallHandler1[GroupId, Future[Option[CustomGroup]]] =
      (mockAccessGroupsRepository
        .findById(_: GroupId))
        .expects(accessGroup.id)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryGetAll(
      accessGroups: Seq[CustomGroup]
    ): CallHandler1[Arn, Future[Seq[CustomGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockAccessGroupsRepositoryInsert(
      accessGroup: CustomGroup,
      maybeCreationId: Option[String]
    ): CallHandler1[CustomGroup, Future[Option[String]]] = (mockAccessGroupsRepository
      .insert(_: CustomGroup))
      .expects(accessGroup)
      .returning(Future.successful(maybeCreationId))

    def mockUserEnrolmentAssignmentServiceCalculateForCreatingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[CustomGroup, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupCreation(_: CustomGroup)(_: ExecutionContext))
        .expects(accessGroup, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupDeletion(_: Arn, _: String)(_: ExecutionContext))
        .expects(arn, groupName, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler4[Arn, String, CustomGroup, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupUpdate(_: Arn, _: String, _: CustomGroup)(_: ExecutionContext))
        .expects(arn, groupName, accessGroup, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler5[Arn, String, Set[Client], Set[AgentUser], ExecutionContext, Future[
      Option[UserEnrolmentAssignments]
    ]] =
      (mockUserEnrolmentAssignmentService
        .calculateForRemoveFromGroup(_: Arn, _: String, _: Set[Client], _: Set[AgentUser])(_: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForAddToGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler5[Arn, String, Set[Client], Set[AgentUser], ExecutionContext, Future[
      Option[UserEnrolmentAssignments]
    ]] =
      (mockUserEnrolmentAssignmentService
        .calculateForAddToGroup(_: Arn, _: String, _: Set[Client], _: Set[AgentUser])(_: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockAccessGroupsRepositoryDelete(
      maybeDeletedCount: Option[Long]
    ): CallHandler2[Arn, String, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .delete(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeDeletedCount))

    def mockAccessGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, CustomGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: CustomGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

    def mockAddTeamMemberToGroup(
      groupId: GroupId,
      member: AgentUser,
      updatedCount: Int = 1
    ): CallHandler2[GroupId, AgentUser, Future[UpdateResult]] =
      (mockAccessGroupsRepository
        .addTeamMember(_: GroupId, _: AgentUser))
        .expects(groupId, member)
        .returning(Future.successful(UpdateResult.acknowledged(updatedCount, updatedCount, null)))

    def mockAddRemoveClientFromGroup(
      groupId: GroupId,
      client: Client,
      updatedCount: Int = 1
    ): CallHandler2[GroupId, String, Future[UpdateResult]] = {
      val updateResult = UpdateResult.acknowledged(updatedCount, updatedCount, null)
      (mockAccessGroupsRepository
        .removeClient(_: GroupId, _: String))
        .expects(groupId, client.enrolmentKey)
        .returning(Future.successful(updateResult))
    }

    def mockTaxGroupsServiceGetGroups(
      groups: Seq[TaxGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[TaxGroup]]] =
      (mockTaxGroupsService
        .getAllTaxServiceGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(groups))

    def mockUserClientDetailsConnectorGetClients(
      maybeClients: Option[Seq[Client]]
    ): CallHandler5[Arn, Boolean, Option[String], HeaderCarrier, ExecutionContext, Future[Option[Seq[Client]]]] =
      (mockUserClientDetailsConnector
        .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *, *, *)
        .returning(Future successful maybeClients)

    def mockUserEnrolmentAssignmentServicePushCalculatedAssignments(
      eacdAssignmentsPushStatus: EacdAssignmentsPushStatus
    ): CallHandler3[Option[UserEnrolmentAssignments], HeaderCarrier, ExecutionContext, Future[
      EacdAssignmentsPushStatus
    ]] =
      (mockUserEnrolmentAssignmentService
        .pushCalculatedAssignments(_: Option[UserEnrolmentAssignments])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful eacdAssignmentsPushStatus)

    def mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(
      maybeOutstandingAssignmentsWorkItemsExist: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful maybeOutstandingAssignmentsWorkItemsExist)

    def mockAucdGetPaginatedClientsForArn(
      arn: Arn,
      page: Int = 1,
      pageSize: Int = 20,
      search: Option[String] = None,
      filter: Option[String] = None
    )(mockedResponse: PaginatedList[Client]): CallHandler7[Arn, Int, Int, Option[String], Option[
      String
    ], HeaderCarrier, ExecutionContext, Future[PaginatedList[Client]]] =
      (mockUserClientDetailsConnector
        .getPaginatedClients(_: Arn)(_: Int, _: Int, _: Option[String], _: Option[String])(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(arn, page, pageSize, search, filter, *, *)
        .returning(Future successful mockedResponse)

    def mockAuditServiceAuditEsAssignmentUnassignments()
      : CallHandler3[UserEnrolmentAssignments, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditEsAssignmentUnassignments(_: UserEnrolmentAssignments)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupCreation(): CallHandler3[CustomGroup, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupCreation(_: CustomGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupUpdate(): CallHandler3[CustomGroup, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupUpdate(_: CustomGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupDeletion()
      : CallHandler5[Arn, String, AgentUser, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupDeletion(_: Arn, _: String, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(())

    def withClientNamesRemoved(accessGroup: CustomGroup): CustomGroup =
      accessGroup.copy(clients = accessGroup.clients.map(_.copy(friendlyName = "")))
  }

  "Calling create" when {

    "group of that name already exists" should {
      s"return $AccessGroupExistsForCreation" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroupInMongo))

        accessGroupsService
          .create(accessGroup)
          .futureValue shouldBe AccessGroupExistsForCreation
      }
    }

    "group of that name does not already exist" when {

      "insert calls returns nothing" should {
        s"return $AccessGroupNotCreated" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForCreatingGroup(None)
          mockAccessGroupsRepositoryGet(None)

          mockAccessGroupsRepositoryInsert(accessGroupInMongo, None)

          accessGroupsService
            .create(accessGroup)
            .futureValue shouldBe AccessGroupNotCreated
        }
      }

      s"insert calls returns an id" when {

        "assignments get pushed" should {
          s"return $AccessGroupCreated" in new TestScope {
            mockAccessGroupsRepositoryGet(None)
            mockUserEnrolmentAssignmentServiceCalculateForCreatingGroup(maybeUserEnrolmentAssignments)
            mockAccessGroupsRepositoryInsert(accessGroupInMongo, Some(insertedId))
            mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
            mockAuditServiceAuditEsAssignmentUnassignments()
            mockAuditServiceAuditAccessGroupCreation()

            accessGroupsService
              .create(accessGroup)
              .futureValue shouldBe AccessGroupCreated(insertedId)
          }
        }

        "assignments do not get pushed" should {
          s"return $AccessGroupCreatedWithoutAssignmentsPushed" in new TestScope {
            mockUserEnrolmentAssignmentServiceCalculateForCreatingGroup(maybeUserEnrolmentAssignments)
            mockAccessGroupsRepositoryGet(None)
            mockAccessGroupsRepositoryInsert(accessGroupInMongo, Some(insertedId))
            mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
            mockAuditServiceAuditAccessGroupCreation()

            accessGroupsService
              .create(accessGroup)
              .futureValue shouldBe AccessGroupCreatedWithoutAssignmentsPushed(insertedId)
          }
        }
      }
    }
  }

  "Fetching all groups" when {

    "access groups exist" should {
      "return corresponding groups" in new TestScope {
        mockAccessGroupsRepositoryGetAll(Seq(accessGroupInMongo))
        mockUserClientDetailsConnectorGetClients(Some(clients))

        accessGroupsService.getAllCustomGroups(arn).futureValue shouldBe
          Seq(accessGroup)
      }
    }

    "no groups found" should {
      "return no access groups" in new TestScope {
        mockAccessGroupsRepositoryGetAll(Seq.empty)

        accessGroupsService.getAllCustomGroups(arn).futureValue shouldBe
          Seq.empty
      }
    }
  }

  "Fetching group" when {

    "group exists" should {
      "return corresponding group" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroupInMongo))
        mockUserClientDetailsConnectorGetClients(Some(clients))

        accessGroupsService.get(arn, groupName).futureValue shouldBe
          Some(accessGroup)
      }
    }

    "group exists but backend does not have matching clients" should {
      "return corresponding group with empty friendly name" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroupInMongo))
        mockUserClientDetailsConnectorGetClients(Some(Seq.empty))

        accessGroupsService.get(arn, groupName).futureValue shouldBe
          Some(accessGroup.copy(clients = accessGroup.clients.map(_.copy(friendlyName = ""))))
      }
    }

    "group does not exists" should {
      "return no group" in new TestScope {
        mockAccessGroupsRepositoryGet(None)

        accessGroupsService.get(arn, groupName).futureValue shouldBe
          None
      }
    }

    "group with no clients" should {
      "return the group" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroup.copy(clients = Set.empty)))
        mockUserClientDetailsConnectorGetClients(Some(clients))

        accessGroupsService.get(arn, groupName).futureValue shouldBe
          Some(accessGroup.copy(clients = Set.empty))
      }
    }
  }

  "Fetching groups for client" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: CustomGroup = accessGroup
        val ag2: CustomGroup = accessGroup.copy(groupName = "group 2", clients = Set(clientVat))

        mockAccessGroupsRepositoryGetAll(
          Seq(withClientNamesRemoved(ag1), withClientNamesRemoved(ag2))
        )

        accessGroupsService
          .getCustomGroupSummariesForClient(arn, s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789")
          .futureValue shouldBe
          Seq(GroupSummary(ag1.id, "some group", Some(3), 3))
      }
    }
  }

  "Fetching groups for team member" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: CustomGroup = accessGroup
        val ag2: CustomGroup = accessGroup.copy(groupName = "group 2", teamMembers = Set(user3))

        mockAccessGroupsRepositoryGetAll(
          Seq(withClientNamesRemoved(ag1), withClientNamesRemoved(ag2))
        )

        accessGroupsService.getCustomGroupSummariesForTeamMember(arn, "user3").futureValue shouldBe
          Seq(GroupSummary(ag2.id, "group 2", Some(3), 1))
      }
    }
  }

  "Fetching group by id" when {

    "group exists" should {
      "return corresponding summaries" in new TestScope {
        (mockAccessGroupsRepository
          .findById(_: GroupId))
          .expects(dbId)
          .returning(Future.successful(Some(accessGroupInMongo)))
        mockUserClientDetailsConnectorGetClients(Some(clients))

        accessGroupsService.getById(dbId).futureValue shouldBe
          Some(accessGroup)
      }
    }
  }

  "Deleting group" when {

    "DB call to delete group returns nothing" should {
      s"return $AccessGroupNotDeleted" in new TestScope {
        mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(None)
        mockAccessGroupsRepositoryDelete(None)

        accessGroupsService.delete(arn, groupName, user).futureValue shouldBe AccessGroupNotDeleted
      }
    }

    "DB call to delete group returns some value" when {

      "DB call to delete group indicates no record was deleted" should {
        s"return $AccessGroupNotDeleted" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryDelete(Some(0L))

          accessGroupsService.delete(arn, groupName, user).futureValue shouldBe AccessGroupNotDeleted
        }
      }

      "DB call to delete group indicates one record was deleted" when {

        "assignments get pushed" should {
          s"return $AccessGroupDeleted" in new TestScope {
            mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(maybeUserEnrolmentAssignments)
            mockAccessGroupsRepositoryDelete(Some(1L))
            mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
            mockAuditServiceAuditEsAssignmentUnassignments()
            mockAuditServiceAuditAccessGroupDeletion()

            accessGroupsService.delete(arn, groupName, user).futureValue shouldBe AccessGroupDeleted
          }
        }

        "assignments do not get pushed" should {
          s"return $AccessGroupDeletedWithoutAssignmentsPushed" in new TestScope {
            mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(maybeUserEnrolmentAssignments)
            mockAccessGroupsRepositoryDelete(Some(1L))
            mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
            mockAuditServiceAuditAccessGroupDeletion()

            accessGroupsService
              .delete(arn, groupName, user)
              .futureValue shouldBe AccessGroupDeletedWithoutAssignmentsPushed
          }
        }
      }
    }

  }

  "Updating group" when {

    "DB call to update group returns nothing" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(None)
        mockAccessGroupsRepositoryUpdate(None)

        accessGroupsService.update(arn, groupName, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
      }
    }

    "DB call to update group indicates no record was updated" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(0))

        accessGroupsService.update(arn, groupName, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
      }
    }

    "DB call to update group indicates one record was updated" when {

      "assignments get pushed" should {
        s"return $AccessGroupUpdated" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(1))
          mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
          mockAuditServiceAuditEsAssignmentUnassignments()
          mockAuditServiceAuditAccessGroupUpdate()

          accessGroupsService.update(arn, groupName, accessGroup, user).futureValue shouldBe AccessGroupUpdated
        }
      }

      "assignments do not get pushed" should {
        s"return $AccessGroupUpdatedWithoutAssignmentsPushed" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(1))
          mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
          mockAuditServiceAuditAccessGroupUpdate()

          accessGroupsService
            .update(arn, groupName, accessGroup, user)
            .futureValue shouldBe AccessGroupUpdatedWithoutAssignmentsPushed
        }
      }
    }

  }

  "Fetching all clients" when {

    "AUCD connector returns nothing" should {
      "return no unassigned clients" in new TestScope {
        mockUserClientDetailsConnectorGetClients(None)
        mockTaxGroupsServiceGetGroups(Seq.empty)

        accessGroupsService.getAllClients(arn).futureValue shouldBe
          ClientList(Set.empty, Set.empty)
      }
    }

    "AUCD connector returns something" when {

      "AUCD connector returns empty list of clients" should {
        "return correct clients" in new TestScope {
          mockUserClientDetailsConnectorGetClients(Some(Seq.empty))
          mockTaxGroupsServiceGetGroups(Seq.empty)

          accessGroupsService.getAllClients(arn).futureValue shouldBe
            ClientList(Set.empty, Set.empty)
        }
      }

      "AUCD connector returns non-empty list of clients" when {

        "no access groups exist" should {
          "return correct clients" in new TestScope {
            val backendClient1: Client = clientVat.copy(friendlyName = "existing client")

            mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
            mockAccessGroupsRepositoryGetAll(Seq.empty)
            mockTaxGroupsServiceGetGroups(Seq.empty)

            accessGroupsService.getAllClients(arn).futureValue shouldBe
              ClientList(Set.empty, Set(backendClient1))
          }
        }

        "access groups exist" when {

          "access group exists whose assigned clients match those returned by AUCD connector" should {
            "return correct clients" in new TestScope {
              val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
              mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
              mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
              mockTaxGroupsServiceGetGroups(Seq.empty)

              accessGroupsService.getAllClients(arn).futureValue shouldBe
                ClientList(Set(backendClient1), Set.empty)
            }
          }

          "access group exists whose assigned clients do not match those returned by AUCD connector" should {
            "return correct clients" in new TestScope {
              val backendClient1: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client")
              mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
              mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
              mockTaxGroupsServiceGetGroups(Seq.empty)

              accessGroupsService.getAllClients(arn).futureValue shouldBe
                ClientList(Set.empty, Set(backendClient1))
            }
          }
        }
      }
    }
  }

  "Fetching assigned clients" when {
    "access group exists whose assigned clients match some of those returned by AUCD connector" should {
      "return correct assigned clients" in new TestScope {
        val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
        val backendClient2: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
        mockTaxGroupsServiceGetGroups(Seq.empty)

        accessGroupsService.getAssignedClients(arn).futureValue shouldBe
          Set(backendClient1)
      }
    }
  }

  "Fetching unassigned clients" when {
    "access group exists whose assigned clients match some of those returned by AUCD connector" should {
      "return correct unassigned clients" in new TestScope {
        val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
        val backendClient2: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockAccessGroupsRepositoryGetAll(Seq(accessGroup))
        mockTaxGroupsServiceGetGroups(Seq.empty)

        accessGroupsService.getUnassignedClients(arn).futureValue shouldBe
          Set(backendClient2)
      }

      s"do not report as 'unassigned' any clients already in tax service groups" in new TestScope {
        val enrolmentKeyVAT = "HMRC-MTD-VAT~VRN~123456789"
        val enrolmentKeyPPT = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
        val enrolmentKeyTrust = "HMRC-TERS-ORG~SAUTR~1731139143"
        val enrolmentKeyTrustNT = "HMRC-TERSNT-ORG~URN~XATRUST73113914"
        val enrolmentKeyCbcUk = "HMRC-CBC-ORG~UTR~1234567890~cbcId~XACBC1234567890"

        val taxServiceAccessGroupPPT: TaxGroup = taxServiceGroup.copy(service = "HMRC-PPT-ORG")
        // These includes both types of trust or cbc client
        val taxServiceAccessGroupTrust: TaxGroup = taxServiceGroup.copy(service = "HMRC-TERS")
        val taxServiceAccessGroupCbc: TaxGroup = taxServiceGroup.copy(service = "HMRC-CBC")

        mockUserClientDetailsConnectorGetClients(
          Some(
            Seq(
              Client(enrolmentKeyVAT, "foo"),
              Client(enrolmentKeyPPT, "bar"),
              Client(enrolmentKeyTrust, "baz"),
              Client(enrolmentKeyTrustNT, "bazNT"),
              Client(enrolmentKeyCbcUk, "alf")
            )
          )
        )
        mockAccessGroupsRepositoryGetAll(Seq.empty)
        mockTaxGroupsServiceGetGroups(
          Seq(taxServiceAccessGroupPPT, taxServiceAccessGroupTrust, taxServiceAccessGroupCbc)
        )

        val result = accessGroupsService.getUnassignedClients(arn).futureValue

        result shouldBe Set(
          Client(enrolmentKeyVAT, "foo")
        ) // don't show the PPT, trust or cbc enrolments as there are already tax service groups for that
      }

      s"DO report as 'unassigned' clients in tax service groups but who are excluded from them" in new TestScope {
        val enrolmentKeyVAT = "HMRC-MTD-VAT~VRN~123456789"
        val enrolmentKeyPPT = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
        val taxServiceAccessGroup =
          taxServiceGroup.copy(service = "HMRC-PPT-ORG", excludedClients = Set(Client(enrolmentKeyPPT, "bar")))

        mockUserClientDetailsConnectorGetClients(
          Some(
            Seq(Client(enrolmentKeyVAT, "foo"), Client(enrolmentKeyPPT, "bar"))
          )
        )
        mockAccessGroupsRepositoryGetAll(Seq.empty)
        mockTaxGroupsServiceGetGroups(Seq(taxServiceAccessGroup))

        val result = accessGroupsService.getUnassignedClients(arn).futureValue

        result shouldBe Set(
          Client(enrolmentKeyVAT, "foo"),
          Client(enrolmentKeyPPT, "bar")
        ) // do show the PPT enrolment as it's excluded from the tax service group
      }
    }
  }

  "Adding team member to a group" when {

    "group found" should {
      s"return $AccessGroupUpdated" in new TestScope {
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForAddToGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
        mockAuditServiceAuditEsAssignmentUnassignments()
        mockAuditServiceAuditAccessGroupUpdate()

        // then
        accessGroupsService
          .addMemberToGroup(accessGroup.id, user, user1)
          .futureValue shouldBe AccessGroupUpdated
      }

      s"return $AccessGroupUpdatedWithoutAssignmentsPushed" in new TestScope {
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForAddToGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
        mockAuditServiceAuditAccessGroupUpdate()

        // then
        accessGroupsService
          .addMemberToGroup(accessGroup.id, user, user1)
          .futureValue shouldBe AccessGroupUpdatedWithoutAssignmentsPushed
      }

      s"return $AccessGroupNotUpdated" when {
        "mongo update count is 0" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForAddToGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(0))
          // then
          accessGroupsService
            .addMemberToGroup(accessGroup.id, user, user1)
            .futureValue shouldBe AccessGroupNotUpdated
        }
        "mongo update count is None" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForAddToGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(None)

          accessGroupsService
            .addMemberToGroup(accessGroup.id, user, user1)
            .futureValue shouldBe AccessGroupNotUpdated
        }
      }
    }

    "group not found" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockAccessGroupsRepositoryFindById(None)
        // then
        accessGroupsService
          .addMemberToGroup(accessGroup.id, user, user1)
          .futureValue shouldBe AccessGroupNotUpdated
      }
    }

  }

  "Removing a client from a group" when {

    "group found" should {
      s"return $AccessGroupUpdated" in new TestScope {
        // expect
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
        mockAuditServiceAuditEsAssignmentUnassignments()
        mockAuditServiceAuditAccessGroupUpdate()

        // when
        private val result =
          accessGroupsService.removeClient(accessGroup.id, clientVat.enrolmentKey, user).futureValue

        // then
        result shouldBe AccessGroupUpdated
      }

      s"return $AccessGroupUpdatedWithoutAssignmentsPushed" in new TestScope {
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
        mockAuditServiceAuditAccessGroupUpdate()
        // then
        accessGroupsService
          .removeClient(accessGroup.id, clientVat.enrolmentKey, user)
          .futureValue shouldBe AccessGroupUpdatedWithoutAssignmentsPushed
      }

      s"return $AccessGroupNotUpdated" when {
        "mongo update count is 0" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(0))
          // then
          accessGroupsService
            .removeClient(accessGroup.id, clientVat.enrolmentKey, user)
            .futureValue shouldBe AccessGroupNotUpdated
        }
        "mongo update count is None" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(None)

          accessGroupsService
            .removeClient(accessGroup.id, clientVat.enrolmentKey, user)
            .futureValue shouldBe AccessGroupNotUpdated
        }
      }
    }

    "group not found" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockAccessGroupsRepositoryFindById(None)
        accessGroupsService
          .removeClient(accessGroup.id, clientVat.enrolmentKey, user)
          .futureValue shouldBe AccessGroupNotUpdated
      }
    }
  }

  "Removing a team members from a group" when {

    s"group found" should {
      s"return $AccessGroupUpdated" in new TestScope {
        // expect
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsPushed)
        mockAuditServiceAuditEsAssignmentUnassignments()
        mockAuditServiceAuditAccessGroupUpdate()

        // when
        private val result =
          accessGroupsService.removeTeamMember(accessGroup.id, user1.id, user).futureValue

        // then
        result shouldBe AccessGroupUpdated
      }

      s"return $AccessGroupUpdatedWithoutAssignmentsPushed" in new TestScope {
        // expect
        mockAccessGroupsRepositoryFindById(Some(accessGroup))
        mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(1))
        mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
        mockAuditServiceAuditAccessGroupUpdate()

        // when
        private val result =
          accessGroupsService.removeTeamMember(accessGroup.id, user1.id, user).futureValue

        // then
        result shouldBe AccessGroupUpdatedWithoutAssignmentsPushed
      }

      s"return $AccessGroupNotUpdated" when {
        "mongo update count is 0" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(0))

          accessGroupsService
            .removeTeamMember(accessGroup.id, randomAlphabetic(8), user)
            .futureValue shouldBe AccessGroupNotUpdated
        }
        "mongo update count is None" in new TestScope {
          mockAccessGroupsRepositoryFindById(Some(accessGroup))
          mockUserEnrolmentAssignmentServiceCalculateForRemoveFromGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(None)

          accessGroupsService
            .removeTeamMember(accessGroup.id, randomAlphabetic(8), user)
            .futureValue shouldBe AccessGroupNotUpdated
        }
      }
    }

    "group not found" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockAccessGroupsRepositoryFindById(None)
        accessGroupsService
          .removeTeamMember(accessGroup.id, randomAlphabetic(8), user)
          .futureValue shouldBe AccessGroupNotUpdated
      }
    }
  }

  "Getting a page of data for purpose of adding clients to a group" when {

    "works as expected when successful " should {
      s"return a paginated list" in new TestScope {
        // given
        private val PAGE = 2
        private val PAGE_SIZE = 10
        private val SEARCH: Some[String] = Some("f")
        private val FILTER: Some[String] = Some("VAT")

        private val mockedResponse: PaginatedList[Client] = PaginatedList[Client](
          accessGroup.clients.toSeq,
          PaginationMetaData(lastPage = false, firstPage = false, 40, 4, 10, 2, 10, None)
        )
        (mockAccessGroupsRepository
          .findById(_: GroupId))
          .expects(dbId)
          .returning(Future.successful(Some(accessGroup)))
        mockAucdGetPaginatedClientsForArn(accessGroup.arn, PAGE, PAGE_SIZE, SEARCH, FILTER)(mockedResponse)

        // when
        val response =
          accessGroupsService
            .getGroupByIdWithPageOfClientsToAdd(dbId, PAGE, PAGE_SIZE, SEARCH, FILTER)
            .futureValue

        // then
        response should not equal None
        response.get._1.groupName shouldBe accessGroup.groupName
        response.get._1.groupId shouldBe accessGroup.id
        response.get._2.pageContent.length shouldBe accessGroup.clients.size
      }
    }
  }

}
