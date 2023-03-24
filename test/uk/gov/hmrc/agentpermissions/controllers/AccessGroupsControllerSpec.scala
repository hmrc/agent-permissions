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

package uk.gov.hmrc.agentpermissions.controllers

import akka.actor.ActorSystem
import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler3, CallHandler4, CallHandler5, CallHandler7}
import play.api.libs.json.{JsArray, JsNumber, JsString, JsValue, Json}
import play.api.mvc.Results.Accepted
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.{AddOneTeamMemberToGroupRequest, DisplayClient}
import uk.gov.hmrc.agentpermissions.service._
import uk.gov.hmrc.auth.core.InvalidBearerToken
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AccessGroupsControllerSpec extends BaseSpec {

  val CONTENTTYPE_APPLICATIONJSON: (String, String) = "Content-Type" -> "application/json"

  val arn: Arn = Arn("KARN0762398")
  val invalidArn: Arn = Arn("KARN0101010")
  val user: AgentUser = AgentUser("userId", "userName")
  val groupName = "some group"
  val createdId = "createdId"
  lazy val now: LocalDateTime = LocalDateTime.now()
  val user1: AgentUser = AgentUser("user1", "User 1")
  val user2: AgentUser = AgentUser("user2", "User 2")
  val dbId: ObjectId = new ObjectId()
  val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
  val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")

  trait TestScope {

    val accessGroup: CustomGroup =
      CustomGroup(dbId, arn, groupName, now, now, user, user, Some(Set.empty), Some(Set.empty))
    val taxServiceGroup: TaxGroup =
      TaxGroup(
        dbId,
        arn,
        groupName,
        now,
        now,
        user,
        user,
        Some(Set.empty),
        serviceVat,
        automaticUpdates = true,
        Some(Set.empty)
      )

    def groupSummary(
      id: ObjectId = dbId,
      name: String = groupName,
      taxService: Option[String] = None
    ): GroupSummary =
      GroupSummary(id.toHexString, name, if (taxService.isEmpty) Some(3) else None, 3, taxService)

    val groupSummaries = Seq(
      groupSummary(),
      groupSummary(dbId, "Capital Gains Tax", taxService = Some(serviceCgt))
    )

    val mockAccessGroupsService: AccessGroupsService = mock[AccessGroupsService]
    val mockGroupsService: GroupsService = mock[GroupsService]
    val mockEacdSynchronizer: EacdSynchronizer = mock[EacdSynchronizer]
    implicit val mockAuthAction: AuthAction = mock[AuthAction]
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val actorSystem: ActorSystem = ActorSystem()

    val controller = new AccessGroupsController(mockAccessGroupsService, mockGroupsService, mockEacdSynchronizer)

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler4[Boolean, Boolean, ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent(_: Boolean, _: Boolean)(_: ExecutionContext, _: Request[_]))
        .expects(*, *, *, *)
        .returning(Future.successful(maybeAuthorisedAgent))

    def mockAuthActionGetAuthorisedAgentWithException(
      ex: Exception
    ): CallHandler4[Boolean, Boolean, ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent(_: Boolean, _: Boolean)(_: ExecutionContext, _: Request[_]))
        .expects(*, *, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceCreate(
      accessGroupCreationStatus: AccessGroupCreationStatus
    ): CallHandler3[CustomGroup, HeaderCarrier, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: CustomGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(accessGroupCreationStatus))

    def mockAccessGroupsServiceCreateWithException(
      ex: Exception
    ): CallHandler3[CustomGroup, HeaderCarrier, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: CustomGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetGroups(
      groups: Seq[CustomGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[CustomGroup]]] =
      (mockAccessGroupsService
        .getAllCustomGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockAccessGroupsServiceGetGroupsWithException(
      ex: Exception
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[CustomGroup]]] =
      (mockAccessGroupsService
        .getAllCustomGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetGroupById(
      maybeAccessGroup: Option[CustomGroup]
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Option[CustomGroup]]] =
      (mockAccessGroupsService
        .getById(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsServiceGetGroupByIdWithException(
      ex: Exception
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Option[CustomGroup]]] =
      (mockAccessGroupsService
        .getById(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future failed ex)

    def mockAccessGroupsServiceGetGroup(
      maybeAccessGroup: Option[CustomGroup]
    ): CallHandler3[GroupId, HeaderCarrier, ExecutionContext, Future[Option[CustomGroup]]] =
      (mockAccessGroupsService
        .get(_: GroupId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GroupId(arn, groupName), *, *)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsServiceGetGroupWithException(
      ex: Exception
    ): CallHandler3[GroupId, HeaderCarrier, ExecutionContext, Future[Option[CustomGroup]]] =
      (mockAccessGroupsService
        .get(_: GroupId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GroupId(arn, groupName), *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceUpdate(
      accessGroupUpdateStatus: AccessGroupUpdateStatus
    ): CallHandler5[GroupId, CustomGroup, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupUpdateStatus]] =
      (mockAccessGroupsService
        .update(_: GroupId, _: CustomGroup, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(accessGroupUpdateStatus))

    def expectAddTeamMemberToGroup(
      accessGroupUpdateStatus: AccessGroupUpdateStatus
    ): CallHandler5[String, AgentUser, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupUpdateStatus]] =
      (mockAccessGroupsService
        .addMemberToGroup(_: String, _: AgentUser, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(accessGroupUpdateStatus))
        .once()

    def expectRemoveClientFromGroup(
      accessGroupUpdateStatus: AccessGroupUpdateStatus
    ): CallHandler5[String, String, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupUpdateStatus]] =
      (mockAccessGroupsService
        .removeClient(_: String, _: String, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(accessGroupUpdateStatus))
        .once()

    def expectRemoveTeamMemberFromGroup(
      accessGroupUpdateStatus: AccessGroupUpdateStatus
    ): CallHandler5[String, String, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupUpdateStatus]] =
      (mockAccessGroupsService
        .removeTeamMember(_: String, _: String, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(accessGroupUpdateStatus))
        .once()

    def mockAccessGroupsServiceDelete(
      accessGroupDeletionStatus: AccessGroupDeletionStatus
    ): CallHandler4[GroupId, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupDeletionStatus]] =
      (mockAccessGroupsService
        .delete(_: GroupId, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.successful(accessGroupDeletionStatus))

    def mockAccessGroupsServiceDeleteWithException(
      ex: Exception
    ): CallHandler4[GroupId, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupDeletionStatus]] =
      (mockAccessGroupsService
        .delete(_: GroupId, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetUnassignedClients(
      unassignedClients: Set[Client]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Set[Client]]] =
      (mockAccessGroupsService
        .getUnassignedClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful unassignedClients)

    def mockAccessGroupsServiceGetPaginatedClientsForUpdate(
      groupId: String,
      page: Int,
      pageSize: Int,
      search: Option[String] = None,
      filter: Option[String] = None
    )(mockResponse: (GroupSummary, PaginatedList[DisplayClient])): CallHandler7[String, Int, Int, Option[
      String
    ], Option[String], HeaderCarrier, ExecutionContext, Future[Option[(GroupSummary, PaginatedList[DisplayClient])]]] =
      (
        mockAccessGroupsService
          .getGroupByIdWithPageOfClientsToAdd(_: String, _: Int, _: Int, _: Option[String], _: Option[String])(
            _: HeaderCarrier,
            _: ExecutionContext
          )
        )
        .expects(groupId, page, pageSize, search, filter, *, *)
        .returning(Future successful Option(mockResponse))

    def mockAccessGroupsServiceGetUnassignedClientsWithException(
      ex: Exception
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Set[Client]]] =
      (mockAccessGroupsService
        .getUnassignedClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockEacdSynchronizerSyncWithEacdNoException(results: Map[SyncResult, Int] = Map.empty): Unit =
      (mockEacdSynchronizer
        .syncWithEacd(_: Arn, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.successful(Some(results)))

    def mockEacdSynchronizerSyncWithEacdHasException(
      ex: Exception
    ): Unit =
      (mockEacdSynchronizer
        .syncWithEacd(_: Arn, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.failed(ex))

    def mockGroupsServiceGetGroupSummariesForClient(
      accessGroupSummaries: Seq[GroupSummary]
    ): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummariesForClient(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockGroupsServiceGetGroupSummariesForClientWithException(
      ex: Exception
    ): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummariesForClient(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.failed(ex))

    def mockGroupsServiceGetGroupSummariesForTeamMember(
      accessGroupSummaries: Seq[GroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockGroupsServiceGetGroupSummariesForTeamMemberWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockGroupsServiceGetGroupSummaries(
      summaries: Seq[GroupSummary]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(summaries))

    def mockGroupsServiceGetGroupSummariesWithException(
      ex: Exception
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockGroupsService
        .getAllGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.failed(ex))

  }

  "Call to create access group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.createGroup(arn)(baseRequest.withBody(jsonPayloadForCreateGroup(groupName)))
        status(result) shouldBe FORBIDDEN
      }
    }

    "auth throws exception" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgentWithException(new InvalidBearerToken("auth failed"))

        val result = controller.createGroup(arn)(baseRequest.withBody(jsonPayloadForCreateGroup(groupName)))
        status(result) shouldBe FORBIDDEN
      }
    }

    "request does not contain json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        val result = controller.createGroup(arn)(baseRequest)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains incorrect json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.createGroup(arn)(baseRequest.withBody(JsString("")))
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains correct json payload" when {

      implicit val request = baseRequest.withBody(jsonPayloadForCreateGroup(groupName))

      "provided arn is not valid" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val invalidArn: Arn = Arn("hello")

          val result = controller.createGroup(invalidArn)(request)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "provided arn is valid" when {

        "provided arn does not match that identified by auth" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

            val nonMatchingArn: Arn = Arn("FARN3782960")

            val result = controller.createGroup(nonMatchingArn)(request)

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is more than the maximum allowed" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

            val result = controller.createGroup(arn)(
              baseRequest
                .withBody(jsonPayloadForCreateGroup("0123456789012345678901234567890123456789012345678901"))
            )

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is less than the maximum allowed" when {

          s"access groups service returns $AccessGroupExistsForCreation" should {
            s"return $CONFLICT" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceCreate(AccessGroupExistsForCreation)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CONFLICT
            }
          }

          s"access groups service returns $AccessGroupCreated" should {
            s"return $CREATED" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceCreate(AccessGroupCreated(createdId))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CREATED
            }
          }

          s"access groups service returns $AccessGroupCreatedWithoutAssignmentsPushed" should {
            s"return $CREATED" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceCreate(AccessGroupCreatedWithoutAssignmentsPushed(createdId))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CREATED
            }
          }

          s"access groups service returns $AccessGroupNotCreated" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceCreate(AccessGroupNotCreated)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe INTERNAL_SERVER_ERROR
            }
          }

          s"access groups service throws an exception" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceCreateWithException(new RuntimeException("boo boo"))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe INTERNAL_SERVER_ERROR
            }
          }

        }
      }

    }
  }

  "Call to fetch ALL group summaries" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.getAllGroupSummaries(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.getAllGroupSummaries(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.getAllGroupSummaries(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch groups returns empty collections" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGroupsServiceGetGroupSummaries(Seq.empty)

          val result = controller.getAllGroupSummaries(arn)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "calls to fetch groups returns data collections" should {

        s"return $OK and groups sorted A-Z" in new TestScope {
          val dbId2 = new ObjectId()

          val sortedGroupSummaries = Seq(
            groupSummary(dbId, "Capital Gains Tax", taxService = Some(serviceCgt)),
            groupSummary(dbId2, "Over done"),
            groupSummary()
          )

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGroupsServiceGetGroupSummaries(sortedGroupSummaries)

          val result = controller.getAllGroupSummaries(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[Seq[GroupSummary]] shouldBe Seq(
            GroupSummary(taxServiceGroup._id.toHexString, "Capital Gains Tax", None, 3, Some(serviceCgt)),
            GroupSummary(dbId2.toHexString, "Over done", Some(3), 3),
            GroupSummary(accessGroup._id.toHexString, accessGroup.groupName, Some(3), 3)
          )
        }
      }

      "call to fetch either access groups throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGroupsServiceGetGroupSummariesWithException(new RuntimeException("boo boo"))

          val result = controller.getAllGroupSummaries(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  "Call to fetch custom groups" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.groups(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.groups(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.groups(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch groups returns empty collections" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroups(Seq.empty)

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "calls to fetch groups returns data collections" should {

        s"return $OK" in new TestScope {

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroups(Seq(accessGroup))

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[Seq[GroupSummary]] shouldBe Seq(
            GroupSummary(accessGroup._id.toHexString, accessGroup.groupName, Some(0), 0)
          )
        }
      }

      "call to fetch access groups throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupsWithException(new RuntimeException("boo boo"))

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  "Call to fetch paginated clients from a group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.getPaginatedClientsForGroup(accessGroup._id.toHexString)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is valid" when {

      "provided arn in group does not match that identified by auth" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.getPaginatedClientsForGroup(accessGroup._id.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      "calls to fetch a group by id returns not found" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(None)

          val result = controller.getPaginatedClientsForGroup(accessGroup._id.toHexString)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "calls to fetch paginated clients from a group returns data" should {

        s"return $OK and paginated list of groups clients sorted A-Z" in new TestScope {
          // given
          val dbId2 = new ObjectId()
          val clients = (1 to 20).map(i => Client("HMRC-MTD-VAT~VRN~101747642", s"Ross Barker $i"))
          val accessGroup2 = accessGroup.copy(_id = dbId2, clients = Some(clients.toSet))

          val sortedClients = clients.sortBy(c => c.friendlyName)

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup2))

          val result = controller.getPaginatedClientsForGroup(accessGroup2._id.toHexString, 1, 10)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[PaginatedList[Client]] shouldBe PaginatedList(
            pageContent = sortedClients.take(10),
            paginationMetaData = PaginationMetaData(
              lastPage = false,
              firstPage = true,
              totalSize = 20,
              totalPages = 2,
              pageSize = 10,
              currentPageNumber = 1,
              currentPageSize = 10
            )
          )
        }
      }

      "call to fetch paginated clients from a group throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupByIdWithException(new RuntimeException("boo boo"))

          val result = controller.getPaginatedClientsForGroup(accessGroup._id.toHexString)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  "Call to fetch clients" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.unassignedClients(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.unassignedClients(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.unassignedClients(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch clients returns empty collections" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClients(Set.empty)

          val result = controller.unassignedClients(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[PaginatedList[Client]].pageContent shouldBe Seq.empty
        }
      }

      "calls to fetch unassigned clients returns data collections" should {

        s"return $OK" in new TestScope {
          val enrolmentKey = "HMRC-MTD-VAT~VRN~123456789"
          val friendlyName = "friendly name"

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClients(Set(Client(enrolmentKey, friendlyName)))

          val result = controller.unassignedClients(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[PaginatedList[Client]].pageContent shouldBe Seq(Client(enrolmentKey, friendlyName))
        }
      }

      "call to fetch clients throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClientsWithException(new RuntimeException("boo boo"))

          val result = controller.unassignedClients(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "requesting unassigned clients" should {
        val client1 = Client("HMRC-MTD-VAT~VRN~123456789", "Friendly Bob")
        val client2 = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Friendly Jane")

        s"paginate correctly" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClients(Set(client1, client2))

          val result = controller.unassignedClients(arn, page = 1, pageSize = 1)(baseRequest)

          status(result) shouldBe OK
          val paginatedList = contentAsJson(result).as[PaginatedList[Client]]
          paginatedList.pageContent shouldBe Seq(client1)
          paginatedList.paginationMetaData.totalPages shouldBe 2
        }

        s"search correctly" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClients(Set(client1, client2))

          val result = controller.unassignedClients(arn, search = Some("bob"))(baseRequest)

          status(result) shouldBe OK
          val paginatedList = contentAsJson(result).as[PaginatedList[Client]]
          paginatedList.pageContent shouldBe Seq(client1)
          paginatedList.paginationMetaData.totalSize shouldBe 1
        }

        s"filter correctly" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetUnassignedClients(Set(client1, client2))

          val result = controller.unassignedClients(arn, filter = Some("HMRC-MTD-VAT"))(baseRequest)

          status(result) shouldBe OK
          val paginatedList = contentAsJson(result).as[PaginatedList[Client]]
          paginatedList.pageContent shouldBe Seq(client1)
          paginatedList.paginationMetaData.totalSize shouldBe 1
        }

      }

    }
  }

  "Call to fetch group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.getGroup(dbId.toHexString)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "group id is not in the expected format" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockAccessGroupsServiceGetGroupById(None)

        val result = controller.getGroup("bad")(baseRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "group id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided group id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      "call to fetch group details returns nothing" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(None)

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch group details returns an access group" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK

          val generatedJson: JsValue = contentAsJson(result)

          (generatedJson \ "arn").get shouldBe JsString(arn.value)
          (generatedJson \ "groupName").get shouldBe JsString(groupName)
          (generatedJson \ "createdBy" \ "id").get shouldBe JsString(user.id)
          (generatedJson \ "createdBy" \ "name").get shouldBe JsString(user.name)
          (generatedJson \ "teamMembers").get shouldBe JsArray(Seq.empty)
          (generatedJson \ "clients").get shouldBe JsArray(Seq.empty)
        }
      }

      "call to fetch group details throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupByIdWithException(new RuntimeException("boo boo"))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }

  "Call to fetch custom group summary" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.getCustomGroupSummary(dbId.toHexString)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "group id is not in the expected format" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockAccessGroupsServiceGetGroupById(None)

        val result = controller.getCustomGroupSummary("bad")(baseRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "group id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided group id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.getCustomGroupSummary(dbId.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      "call to fetch group details returns nothing" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(None)

          val result = controller.getCustomGroupSummary(dbId.toHexString)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch group details returns an access group" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.getCustomGroupSummary(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK

          val generatedJson: JsValue = contentAsJson(result)

          (generatedJson \ "groupId").get shouldBe JsString(dbId.toHexString)
          (generatedJson \ "groupName").get shouldBe JsString(groupName)
          (generatedJson \ "clientCount").get shouldBe JsNumber(0)
          (generatedJson \ "teamMemberCount").get shouldBe JsNumber(0)
        }
      }

      "call to fetch group details throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupByIdWithException(new RuntimeException("boo boo"))

          val result = controller.getCustomGroupSummary(dbId.toHexString)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }

  "Call to get groups summaries for client" should {

    "return only groups that the client is in" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForClient(Seq(groupSummary()))

      val result = controller.getGroupSummariesForClient(arn, "key")(baseRequest)

      status(result) shouldBe OK

      contentAsJson(result) shouldBe Json.parse(
        s"""[{"groupId":"${dbId.toHexString}","groupName":"$groupName","clientCount":3,"teamMemberCount":3}]"""
      )

    }

    "return Not Found if there are no groups for the client" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForClient(Seq.empty)

      val result = controller.getGroupSummariesForClient(arn, "key")(baseRequest)

      status(result) shouldBe NOT_FOUND
    }

    s"return $INTERNAL_SERVER_ERROR if there was some exception" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForClientWithException(new NullPointerException("bad"))

      val result = controller.getGroupSummariesForClient(arn, "key")(baseRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "Call to get groups summaries for a team member" should {

    "return only groups that the team member is in" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForTeamMember(Seq(groupSummary()))

      val result = controller.getGroupSummariesForTeamMember(arn, "key")(baseRequest)

      status(result) shouldBe OK

      contentAsJson(result) shouldBe Json.parse(
        s"""[{"groupId":"${dbId.toHexString}","groupName":"$groupName","clientCount":3,"teamMemberCount":3}]"""
      )

    }

    "return Not Found if there are no groups for the team member" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForTeamMember(Seq.empty)

      val result = controller.getGroupSummariesForTeamMember(arn, "key")(baseRequest)

      status(result) shouldBe NOT_FOUND
    }

    s"return $INTERNAL_SERVER_ERROR if there was some exception" in new TestScope {
      mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
      mockGroupsServiceGetGroupSummariesForTeamMemberWithException(new NullPointerException("bad"))

      val result = controller.getGroupSummariesForTeamMember(arn, "key")(baseRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "Call to delete group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.deleteGroup(dbId.toHexString)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "group id is not in the expected format" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockAccessGroupsServiceGetGroupById(None)

        val result = controller.deleteGroup("bad")(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "group id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided group id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      s"access groups service returns $AccessGroupNotDeleted" should {
        s"return $NOT_MODIFIED" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))
          mockAccessGroupsServiceDelete(AccessGroupNotDeleted)

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe NOT_MODIFIED
        }
      }

      s"access groups service returns $AccessGroupDeleted" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))
          mockAccessGroupsServiceDelete(AccessGroupDeleted)

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK
        }
      }

      s"access groups service returns $AccessGroupDeletedWithoutAssignmentsPushed" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))
          mockAccessGroupsServiceDelete(AccessGroupDeletedWithoutAssignmentsPushed)

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK
        }
      }

      s"access groups service throws an exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(Some(accessGroup))
          mockAccessGroupsServiceDeleteWithException(new RuntimeException("boo boo"))

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }

  "Call to update group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result =
          controller.updateGroup(dbId.toHexString)(baseRequest.withBody(jsonPayloadForUpdatingGroup(groupName)))
        status(result) shouldBe FORBIDDEN
      }
    }

    "request does not contain json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        val result = controller.updateGroup("bad")(baseRequest)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains incorrect json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.updateGroup(dbId.toHexString)(baseRequest.withBody(JsString("")))
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains correct json payload" when {

      implicit val request = baseRequest.withBody(jsonPayloadForUpdatingGroup(groupName))

      "group id is not in the expected format" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(None)

          val result = controller.updateGroup("bad")(request)
          status(result) shouldBe BAD_REQUEST
        }
      }

      "group id is in the expected format" when {

        "auth identifies a different arn than that obtained from provided group id" should {
          s"return $FORBIDDEN" in new TestScope {
            val nonMatchingArn: Arn = Arn("FARN3782960")

            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
            mockAccessGroupsServiceGetGroupById(Some(accessGroup))

            val result = controller.updateGroup(dbId.toHexString)(request)

            status(result) shouldBe FORBIDDEN
          }
        }

        "group for provided id does not exist" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(None)

            val result = controller.updateGroup(dbId.toHexString)(request)

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is more than the maximum allowed" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(Some(accessGroup))

            val result = controller.updateGroup(dbId.toHexString)(
              baseRequest
                .withBody(jsonPayloadForUpdatingGroup("0123456789012345678901234567890123456789012345678901"))
            )

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is less than the maximum allowed" when {

          s"access groups service returns $AccessGroupNotUpdated" should {
            s"return $NOT_FOUND" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceGetGroupById(Some(accessGroup))
              mockAccessGroupsServiceUpdate(AccessGroupNotUpdated)

              val result = controller.updateGroup(dbId.toHexString)(request)

              status(result) shouldBe NOT_FOUND
            }
          }

          s"access groups service returns $AccessGroupUpdated" should {
            s"return $OK" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceGetGroupById(Some(accessGroup))
              mockAccessGroupsServiceUpdate(AccessGroupUpdated)

              val result = controller.updateGroup(dbId.toHexString)(request)

              status(result) shouldBe OK
            }
          }

          s"access groups service returns $AccessGroupUpdatedWithoutAssignmentsPushed" should {
            s"return $OK" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceGetGroupById(Some(accessGroup))
              mockAccessGroupsServiceUpdate(AccessGroupUpdatedWithoutAssignmentsPushed)

              val result = controller.updateGroup(dbId.toHexString)(request)

              status(result) shouldBe OK
            }
          }
        }
      }
    }
  }

  "Call to add team member to a group" when {
    s"agent not authorised should return $FORBIDDEN" in new TestScope {
      // given
      mockAuthActionGetAuthorisedAgent(None)
      // when
      val result =
        controller.addTeamMemberToGroup(dbId.toHexString)(
          baseRequest.withBody(jsonPayloadForUpdatingGroup(groupName))
        )

      // then
      status(result) shouldBe FORBIDDEN
    }

    "agent authorised" should {
      s"return $OK" when {
        "payload is good and assignments NOT pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          // when
          val request: AddOneTeamMemberToGroupRequest = AddOneTeamMemberToGroupRequest(user)
          expectAddTeamMemberToGroup(AccessGroupUpdatedWithoutAssignmentsPushed)

          // then
          val result = controller.addTeamMemberToGroup(dbId.toHexString)(baseRequest.withBody(Json.toJson(request)))
          status(result) shouldBe OK
        }

        "payload is good and assignments pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          // when
          val request: AddOneTeamMemberToGroupRequest = AddOneTeamMemberToGroupRequest(user)
          expectAddTeamMemberToGroup(AccessGroupUpdated)

          // then
          val result = controller.addTeamMemberToGroup(dbId.toHexString)(baseRequest.withBody(Json.toJson(request)))
          status(result) shouldBe OK
        }
      }

      s"return $NOT_FOUND when group is not updated" in new TestScope {
        // given
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        // when
        val request: AddOneTeamMemberToGroupRequest = AddOneTeamMemberToGroupRequest(user)

        // then
        expectAddTeamMemberToGroup(AccessGroupNotUpdated)
        val result = controller.addTeamMemberToGroup(dbId.toHexString)(baseRequest.withBody(Json.toJson(request)))
        status(result) shouldBe NOT_FOUND
      }
    }
  }

  "Call to remove a client from to a group" when {

    s"agent not authorised should return $FORBIDDEN" in new TestScope {
      // given
      mockAuthActionGetAuthorisedAgent(None)
      // when
      val result = controller.removeClient(dbId.toHexString, "whatever")(baseRequest)

      // then
      status(result) shouldBe FORBIDDEN
    }

    "agent authorised" should {

      s"return $NO_CONTENT if successfully removed client" when {
        "assignments pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          expectRemoveClientFromGroup(AccessGroupUpdated)

          // when
          val result = controller.removeClient(dbId.toHexString, "whatever")(baseRequest)

          // then
          status(result) shouldBe NO_CONTENT
        }

        "without assignments pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          expectRemoveClientFromGroup(AccessGroupUpdatedWithoutAssignmentsPushed)

          // when
          val result = controller.removeClient(dbId.toHexString, "whatever")(baseRequest)

          // then
          status(result) shouldBe NO_CONTENT
        }
      }

      s"return $NOT_MODIFIED when client or group not found" in new TestScope {
        // given
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        expectRemoveClientFromGroup(AccessGroupNotUpdated)

        // when
        val result = controller.removeClient(dbId.toHexString, "whatever")(baseRequest)

        // then
        status(result) shouldBe NOT_MODIFIED
      }
    }
  }

  "Call to remove a team member from to a group" when {

    s"agent not authorised should return $FORBIDDEN" in new TestScope {
      // given
      mockAuthActionGetAuthorisedAgent(None)
      // when
      val result = controller.removeClient(dbId.toHexString, "whatever")(baseRequest)

      // then
      status(result) shouldBe FORBIDDEN
    }

    "agent is authorised" should {

      s"return $NO_CONTENT if successfully removed team member" when {
        "assignments pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          expectRemoveTeamMemberFromGroup(AccessGroupUpdated)

          // when
          val result = controller.removeTeamMember(dbId.toHexString, "a valid id")(baseRequest)

          // then
          status(result) shouldBe NO_CONTENT
        }

        "without assignments pushed" in new TestScope {
          // given
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          expectRemoveTeamMemberFromGroup(AccessGroupUpdatedWithoutAssignmentsPushed)

          // when
          val result = controller.removeTeamMember(dbId.toHexString, "a valid id")(baseRequest)

          // then
          status(result) shouldBe NO_CONTENT
        }
      }

      s"return $NOT_MODIFIED if team member or group not found" in new TestScope {
        // given
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        expectRemoveTeamMemberFromGroup(AccessGroupNotUpdated)

        // when
        val result = controller.removeTeamMember(dbId.toHexString, "invalid id")(baseRequest)

        // then
        status(result) shouldBe NOT_MODIFIED
      }
    }
  }

  "Call to check group name" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.groupNameCheck(arn, groupName)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.groupNameCheck(invalidArn, groupName)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.groupNameCheck(nonMatchingArn, groupName)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "call to fetch access groups summaries returns empty collections" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGroupsServiceGetGroupSummaries(Seq.empty)

          val result = controller.groupNameCheck(arn, groupName)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "call to fetch access groups returns group summaries" when {

        "existing access groups contain a group whose name matches (even case-insensitively) that is being checked" should {
          s"return $CONFLICT" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockGroupsServiceGetGroupSummaries(groupSummaries)

            val result = controller.groupNameCheck(arn, groupName.toUpperCase)(baseRequest)

            status(result) shouldBe CONFLICT
          }
        }

        "existing access groups contain a group whose name matches (except whitespace) that is being checked" should {
          s"return $CONFLICT" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockGroupsServiceGetGroupSummaries(groupSummaries)

            val result = controller.groupNameCheck(arn, " " + groupName)(baseRequest)

            status(result) shouldBe CONFLICT
          }
        }

        "existing access groups do not contain any group whose name matches that is being checked" should {
          s"return $OK" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockGroupsServiceGetGroupSummaries(groupSummaries)

            val result = controller.groupNameCheck(arn, "non existing group")(baseRequest)

            status(result) shouldBe OK
          }
        }
      }

    }
  }

  "Call to sync with EACD" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.syncWithEacd(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.syncWithEacd(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.syncWithEacd(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "provided arn matches that identified by auth" when {

        "sync is successful" should {
          s"return $OK" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockEacdSynchronizerSyncWithEacdNoException(Map(SyncResult.AccessGroupUpdateSuccess -> 42))

            val result = controller.syncWithEacd(arn)(baseRequest)

            status(result) shouldBe ACCEPTED
          }
        }

        "sync throws exception" should {
          s"return $OK" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockEacdSynchronizerSyncWithEacdHasException(new RuntimeException("boo boo"))

            val result = controller.syncWithEacd(arn)(baseRequest)

            status(result) shouldBe ACCEPTED
          }
        }
      }

    }
  }

  "Call to add unassigned clients/team members to a group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.addUnassignedMembers(dbId.toHexString)(
          baseRequest.withBody(jsonPayloadForUpdatingGroup(groupName))
        )
        status(result) shouldBe FORBIDDEN
      }
    }

    "request does not contain json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        val result = controller.addUnassignedMembers("bad")(baseRequest)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains incorrect json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.addUnassignedMembers(dbId.toHexString)(baseRequest.withBody(JsString("")))
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains correct json payload" when {

      implicit val request = baseRequest.withBody(jsonPayloadForAddingMembersToAGroup(groupName))

      "group id is not in the expected format" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupById(None)

          val result = controller.addUnassignedMembers("bad")(request)
          status(result) shouldBe BAD_REQUEST
        }
      }

      "group id is in the expected format" when {

        s"access groups service returns $AccessGroupUpdated" should {
          s"return $OK" in new TestScope {

            val group = accessGroup.copy(
              teamMembers = Some(Set(AgentUser("1", "existing"))),
              clients = Some(Set(Client("whatever", "friendly")))
            )
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(Some(group))
            mockAccessGroupsServiceUpdate(AccessGroupUpdated)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe OK
          }
        }

        s"access groups service returns $AccessGroupUpdatedWithoutAssignmentsPushed" should {
          s"return $OK" in new TestScope {
            val group = accessGroup.copy(
              teamMembers = Some(Set(AgentUser("1", "existing"))),
              clients = Some(Set(Client("whatever", "friendly")))
            )
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(Some(group))
            mockAccessGroupsServiceUpdate(AccessGroupUpdatedWithoutAssignmentsPushed)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe OK
          }
        }

        s"access groups service returns $AccessGroupNotUpdated" should {
          s"return $NOT_FOUND" in new TestScope {
            val group = accessGroup.copy(
              teamMembers = Some(Set(AgentUser("1", "existing"))),
              clients = Some(Set(Client("whatever", "friendly")))
            )
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(Some(group))
            mockAccessGroupsServiceUpdate(AccessGroupNotUpdated)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe NOT_FOUND
          }
        }

        "group for provided id does not exist" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroupById(None)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe BAD_REQUEST
          }
        }

      }
    }
  }

  "Call to fetch GroupSummary and PaginatedClientsForUpdate" when {
    "provided groupId is valid" should {
      s"return $OK" in new TestScope {
        // given
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        private val PAGE = 2
        private val PAGE_SIZE = 10
        private val SEARCH: Some[String] = Some("f")
        private val FILTER: Some[String] = Some("VAT")
        private val groupId: String = accessGroup._id.toString
        private val paginationMetaData: PaginationMetaData = PaginationMetaData(false, true, 50, 5, 10, 22, 10, None)
        private val response: (GroupSummary, PaginatedList[DisplayClient]) =
          (groupSummaries.head, PaginatedList[DisplayClient](Seq.empty, paginationMetaData))
        mockAccessGroupsServiceGetPaginatedClientsForUpdate(groupId, PAGE, PAGE_SIZE, SEARCH, FILTER)(response)

        // when
        val result =
          controller.getPaginatedClientsForAddingToGroup(groupId, PAGE, PAGE_SIZE, SEARCH, FILTER)(baseRequest)

        // then
        status(result) shouldBe OK
      }
    }

    "provided groupId is not found" should {
      s"return $NOT_FOUND" in new TestScope {
        // given
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        private val PAGE = 2
        private val PAGE_SIZE = 10
        private val SEARCH: Some[String] = Some("f")
        private val FILTER: Some[String] = Some("VAT")
        private val groupId: String = accessGroup._id.toString
        mockAccessGroupsServiceGetPaginatedClientsForUpdate(groupId, PAGE, PAGE_SIZE, SEARCH, FILTER)(null)

        // when
        val result =
          controller.getPaginatedClientsForAddingToGroup(groupId, PAGE, PAGE_SIZE, SEARCH, FILTER)(baseRequest)

        // then
        status(result) shouldBe NOT_FOUND
      }
    }
  }

  def jsonPayloadForCreateGroup(groupName: String): JsValue =
    Json.parse(s"""{
                  |    "groupName": "$groupName",
                  |    "clients": ${Json.toJson(Seq(clientVat, clientPpt))},
                  |    "teamMembers": ${Json.toJson(Seq(user1, user2))}
                  |}""".stripMargin)

  def jsonPayloadForUpdatingGroup(groupName: String): JsValue =
    Json.parse(s"""{
                  |    "groupName": "$groupName",
                  |    "teamMembers": [
                  |        {
                  |            "id": "user2",
                  |            "name": "User 2"
                  |        },
                  |        {
                  |            "id": "user3",
                  |            "name": "User 3"
                  |        }
                  |    ],
                  |    "clients": [
                  |        {
                  |            "enrolmentKey": "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345",
                  |            "friendlyName": "Frank Wright"
                  |        }
                  |    ]
                  |}""".stripMargin)

  def jsonPayloadForAddingMembersToAGroup(groupName: String): JsValue =
    Json.parse(s"""{
                  |   "teamMembers": [
                  |        {
                  |            "id": "user2",
                  |            "name": "User 2"
                  |        },
                  |        {
                  |            "id": "user3",
                  |            "name": "User 3"
                  |        }
                  |    ],
                  |    "clients": [
                  |        {
                  |            "enrolmentKey": "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345",
                  |            "friendlyName": "Frank Wright"
                  |        }
                  |    ]
                  |}""".stripMargin)

  def baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(CONTENTTYPE_APPLICATIONJSON)
}
