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

import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3, CallHandler5}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.{AssignmentsNotPushed, AssignmentsPushed, EacdAssignmentsPushStatus, UserClientDetailsConnector}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agentpermissions.service.userenrolment.UserEnrolmentAssignmentService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AccessGroupsServiceSpec extends BaseSpec {

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

    val groupId: GroupId = GroupId(arn, groupName)
    val dbId: String = new ObjectId().toHexString

    val accessGroup: AccessGroup = AccessGroup(
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      Some(Set(clientVat, clientPpt, clientCgt))
    )

    val clients = Seq(clientVat, clientPpt, clientCgt)

    val accessGroupInMongo: AccessGroup = withClientNamesRemoved(accessGroup)

    val assignedClient: AssignedClient = AssignedClient("service~key~value", None, "user")

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]
    val mockUserEnrolmentAssignmentService: UserEnrolmentAssignmentService = mock[UserEnrolmentAssignmentService]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockAuditService: AuditService = mock[AuditService]

    val accessGroupsService: AccessGroupsService =
      new AccessGroupsServiceImpl(
        mockAccessGroupsRepository,
        mockUserEnrolmentAssignmentService,
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
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler2[Arn, String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryGetById(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler1[String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsRepository
        .findById(_: String))
        .expects(dbId)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryGetAll(
      accessGroups: Seq[AccessGroup]
    ): CallHandler1[Arn, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockAccessGroupsRepositoryInsert(
      accessGroup: AccessGroup,
      maybeCreationId: Option[String]
    ): CallHandler1[AccessGroup, Future[Option[String]]] = (mockAccessGroupsRepository
      .insert(_: AccessGroup))
      .expects(accessGroup)
      .returning(Future.successful(maybeCreationId))

    def mockUserEnrolmentAssignmentServiceCalculateForCreatingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[AccessGroup, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupCreation(_: AccessGroup)(_: ExecutionContext))
        .expects(accessGroup, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler2[GroupId, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupDeletion(_: GroupId)(_: ExecutionContext))
        .expects(groupId, *)
        .returning(Future successful maybeUserEnrolmentAssignments)

    def mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(
      maybeUserEnrolmentAssignments: Option[UserEnrolmentAssignments]
    ): CallHandler3[GroupId, AccessGroup, ExecutionContext, Future[Option[UserEnrolmentAssignments]]] =
      (mockUserEnrolmentAssignmentService
        .calculateForGroupUpdate(_: GroupId, _: AccessGroup)(_: ExecutionContext))
        .expects(groupId, accessGroup, *)
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
    ): CallHandler3[Arn, String, AccessGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: AccessGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

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

    def mockUserClientDetailsConnectorGetClientsWithAssignedUsers(
      maybeGroupDelegatedEnrolments: Option[GroupDelegatedEnrolments]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[GroupDelegatedEnrolments]]] =
      (mockUserClientDetailsConnector
        .getClientsWithAssignedUsers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful maybeGroupDelegatedEnrolments)

    def mockUserClientDetailsConnectorOutstandingAssignmentsWorkItemsExist(
      maybeOutstandingAssignmentsWorkItemsExist: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .outstandingAssignmentsWorkItemsExist(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful maybeOutstandingAssignmentsWorkItemsExist)

    def mockAuditServiceAuditEsAssignmentUnassignments()
      : CallHandler3[UserEnrolmentAssignments, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditEsAssignmentUnassignments(_: UserEnrolmentAssignments)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupCreation(): CallHandler3[AccessGroup, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupCreation(_: AccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupUpdate(): CallHandler3[AccessGroup, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupUpdate(_: AccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(())

    def mockAuditServiceAuditAccessGroupDeletion()
      : CallHandler5[Arn, String, AgentUser, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupDeletion(_: Arn, _: String, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(())

    def withClientNamesRemoved(accessGroup: AccessGroup): AccessGroup =
      accessGroup.copy(clients = accessGroup.clients.map(_.map(_.copy(friendlyName = ""))))
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

        accessGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup)
      }
    }

    "group exists but backend does not have matching clients" should {
      "return corresponding group with empty friendly name" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroupInMongo))
        mockUserClientDetailsConnectorGetClients(Some(Seq.empty))

        accessGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup.copy(clients = accessGroup.clients.map(_.map(_.copy(friendlyName = "")))))
      }
    }

    "group does not exists" should {
      "return no group" in new TestScope {
        mockAccessGroupsRepositoryGet(None)

        accessGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          None
      }
    }

    "group with no clients" should {
      "return the group" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroup.copy(clients = None)))
        mockUserClientDetailsConnectorGetClients(Some(clients))

        accessGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup.copy(clients = None))
      }
    }
  }

  "Fetching groups for client" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: AccessGroup = accessGroup
        val ag2: AccessGroup = accessGroup.copy(groupName = "group 2", clients = Some(Set(clientVat)))

        mockAccessGroupsRepositoryGetAll(
          Seq(withClientNamesRemoved(ag1), withClientNamesRemoved(ag2))
        )

        accessGroupsService
          .getCustomGroupSummariesForClient(arn, s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789")
          .futureValue shouldBe
          Seq(AccessGroupSummary(ag1._id.toHexString, "some group", Some(3), 3, ""))
      }
    }
  }

  "Fetching groups for team member" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: AccessGroup = accessGroup
        val ag2: AccessGroup = accessGroup.copy(groupName = "group 2", teamMembers = Some(Set(user3)))

        mockAccessGroupsRepositoryGetAll(
          Seq(withClientNamesRemoved(ag1), withClientNamesRemoved(ag2))
        )

        accessGroupsService.getCustomGroupSummariesForTeamMember(arn, "user3").futureValue shouldBe
          Seq(AccessGroupSummary(ag2._id.toHexString, "group 2", Some(3), 1, ""))
      }
    }
  }

  "Fetching group by id" when {

    "group exists" should {
      "return corresponding summaries" in new TestScope {
        mockAccessGroupsRepositoryGetById(Some(accessGroupInMongo))
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

        accessGroupsService.delete(groupId, user).futureValue shouldBe AccessGroupNotDeleted
      }
    }

    "DB call to delete group returns some value" when {

      "DB call to delete group indicates no record was deleted" should {
        s"return $AccessGroupNotDeleted" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryDelete(Some(0L))

          accessGroupsService.delete(groupId, user).futureValue shouldBe AccessGroupNotDeleted
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

            accessGroupsService.delete(groupId, user).futureValue shouldBe AccessGroupDeleted
          }
        }

        "assignments do not get pushed" should {
          s"return $AccessGroupDeletedWithoutAssignmentsPushed" in new TestScope {
            mockUserEnrolmentAssignmentServiceCalculateForDeletingGroup(maybeUserEnrolmentAssignments)
            mockAccessGroupsRepositoryDelete(Some(1L))
            mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
            mockAuditServiceAuditAccessGroupDeletion()

            accessGroupsService.delete(groupId, user).futureValue shouldBe AccessGroupDeletedWithoutAssignmentsPushed
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

        accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
      }
    }

    "DB call to update group indicates no record was updated" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(maybeUserEnrolmentAssignments)
        mockAccessGroupsRepositoryUpdate(Some(0))

        accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
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

          accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupUpdated
        }
      }

      "assignments do not get pushed" should {
        s"return $AccessGroupUpdatedWithoutAssignmentsPushed" in new TestScope {
          mockUserEnrolmentAssignmentServiceCalculateForUpdatingGroup(maybeUserEnrolmentAssignments)
          mockAccessGroupsRepositoryUpdate(Some(1))
          mockUserEnrolmentAssignmentServicePushCalculatedAssignments(AssignmentsNotPushed)
          mockAuditServiceAuditAccessGroupUpdate()

          accessGroupsService
            .update(groupId, accessGroup, user)
            .futureValue shouldBe AccessGroupUpdatedWithoutAssignmentsPushed
        }
      }
    }

  }

  "Fetching all clients" when {

    "AUCD connector returns nothing" should {
      "return no unassigned clients" in new TestScope {
        mockUserClientDetailsConnectorGetClients(None)

        accessGroupsService.getAllClients(arn).futureValue shouldBe
          ClientList(Set.empty, Set.empty)
      }
    }

    "AUCD connector returns something" when {

      "AUCD connector returns empty list of clients" should {
        "return correct clients" in new TestScope {
          mockUserClientDetailsConnectorGetClients(Some(Seq.empty))

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

              accessGroupsService.getAllClients(arn).futureValue shouldBe
                ClientList(Set(backendClient1), Set.empty)
            }
          }

          "access group exists whose assigned clients do not match those returned by AUCD connector" should {
            "return correct clients" in new TestScope {
              val backendClient1: Client = Client("unmatchedEnrolmentKey", "existing client")
              mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
              mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

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
        val backendClient2: Client = Client("unmatchedEnrolmentKey", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

        accessGroupsService.getAssignedClients(arn).futureValue shouldBe
          Set(backendClient1)
      }
    }
  }

  "Fetching unassigned clients" when {
    "access group exists whose assigned clients match some of those returned by AUCD connector" should {
      "return correct unassigned clients" in new TestScope {
        val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
        val backendClient2: Client = Client("unmatchedEnrolmentKey", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

        accessGroupsService.getUnassignedClients(arn).futureValue shouldBe
          Set(backendClient2)
      }
    }
  }

}
