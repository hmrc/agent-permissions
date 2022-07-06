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

package uk.gov.hmrc.agentpermissions.controllers

import akka.actor.ActorSystem
import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3, CallHandler5}
import play.api.libs.json.{JsArray, JsString, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
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
  val enrolment1: Enrolment =
    Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2: Enrolment = Enrolment(
    "HMRC-PPT-ORG",
    "Activated",
    "Frank Wright",
    Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
  )

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
        mockAuthActionGetAuthorisedAgentWithExceptiom(new InvalidBearerToken("auth failed"))

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
                .withBody(jsonPayloadForCreateGroup("0123456789012345678901234567890123"))
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

  "Call to fetch group summaries" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.groupsSummaries(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.groupsSummaries(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.groupsSummaries(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch both access groups and unassigned clients return empty collections" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroups(Seq.empty)
          mockAccessGroupsServiceGetUnassignedClients(Set.empty)

          val result = controller.groupsSummaries(arn)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "not both calls to fetch access groups and unassigned clients return empty collections" when {

        "call to fetch unassigned clients returns empty collection" when {

          "access group clients and users are nothing" should {
            s"return $OK" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

              val groupWithEmptyClientsAndUsers: AccessGroup = accessGroup.copy(clients = None, teamMembers = None)
              mockAccessGroupsServiceGetGroups(Seq(groupWithEmptyClientsAndUsers))
              mockAccessGroupsServiceGetUnassignedClients(Set.empty)

              val result = controller.groupsSummaries(arn)(baseRequest)

              status(result) shouldBe OK
              contentAsJson(result) shouldBe Json.parse(
                s"""{"groups":[{"groupId":"${dbId.toHexString}","groupName":"$groupName","clientCount":0,"teamMemberCount":0}],"unassignedClients":[]}"""
              )
            }
          }

          "access group clients and users are not nothing" should {
            s"return $OK" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceGetGroups(Seq(accessGroup))
              mockAccessGroupsServiceGetUnassignedClients(Set.empty)

              val result = controller.groupsSummaries(arn)(baseRequest)

              status(result) shouldBe OK
              contentAsJson(result) shouldBe Json.parse(
                s"""{"groups":[{"groupId":"${dbId.toHexString}","groupName":"$groupName","clientCount":0,"teamMemberCount":0}],"unassignedClients":[]}"""
              )
            }
          }
        }

        "call to fetch unassigned clients returns non-empty collection" when {

          "access group clients and users are not nothing" should {
            s"return $OK" in new TestScope {
              val enrolmentKey = "key"
              val friendlyName = "friendly name"

              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockAccessGroupsServiceGetGroups(Seq(accessGroup))
              mockAccessGroupsServiceGetUnassignedClients(Set(Client(enrolmentKey, friendlyName)))

              val result = controller.groupsSummaries(arn)(baseRequest)

              status(result) shouldBe OK
              contentAsJson(result) shouldBe Json.parse(
                s"""{"groups":[{"groupId":"${dbId.toHexString}","groupName":"$groupName","clientCount":0,"teamMemberCount":0}],"unassignedClients":[{"enrolmentKey":"$enrolmentKey","friendlyName":"$friendlyName"}]}"""
              )
            }
          }
        }
      }

      "call to fetch access groups throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroupsWithException(new RuntimeException("boo boo"))

          val result = controller.groupsSummaries(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
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
                .withBody(jsonPayloadForUpdatingGroup("0123456789012345678901234567890123"))
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

      "call to fetch access groups returns empty collection" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockAccessGroupsServiceGetGroups(Seq.empty)

          val result = controller.groupNameCheck(arn, groupName)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "call to fetch access groups returns non-empty collection" when {

        "existing access groups contain a group whose name matches (even case-insensitively) that is being checked" should {
          s"return $CONFLICT" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroups(Seq(accessGroup))

            val result = controller.groupNameCheck(arn, groupName.toUpperCase)(baseRequest)

            status(result) shouldBe CONFLICT
          }
        }

        "existing access groups contain a group whose name matches (except whitespace) that is being checked" should {
          s"return $CONFLICT" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroups(Seq(accessGroup))

            val result = controller.groupNameCheck(arn, " " + groupName)(baseRequest)

            status(result) shouldBe CONFLICT
          }
        }

        "existing access groups do not contain any group whose name matches that is being checked" should {
          s"return $OK" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockAccessGroupsServiceGetGroups(Seq(accessGroup))

            val result = controller.groupNameCheck(arn, "non existing group")(baseRequest)

            status(result) shouldBe OK
          }
        }
      }
    }
  }

  trait TestScope {

    val accessGroup: AccessGroup =
      AccessGroup(dbId, arn, groupName, now, now, user, user, Some(Set.empty), Some(Set.empty))
    val mockAccessGroupsService: AccessGroupsService = mock[AccessGroupsService]
    val mockAuthAction: AuthAction = mock[AuthAction]
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val actorSystem: ActorSystem = ActorSystem()

    val controller = new AccessGroupsController(mockAccessGroupsService, mockAuthAction)

    def mockAuthActionGetAuthorisedAgent(
      maybeAuthorisedAgent: Option[AuthorisedAgent]
    ): CallHandler2[ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent()(_: ExecutionContext, _: Request[_]))
        .expects(*, *)
        .returning(Future.successful(maybeAuthorisedAgent))

    def mockAuthActionGetAuthorisedAgentWithExceptiom(
      ex: Exception
    ): CallHandler2[ExecutionContext, Request[_], Future[Option[AuthorisedAgent]]] =
      (mockAuthAction
        .getAuthorisedAgent()(_: ExecutionContext, _: Request[_]))
        .expects(*, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceCreate(
      accessGroupCreationStatus: AccessGroupCreationStatus
    ): CallHandler3[AccessGroup, HeaderCarrier, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: AccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(accessGroupCreationStatus))

    def mockAccessGroupsServiceCreateWithException(
      ex: Exception
    ): CallHandler3[AccessGroup, HeaderCarrier, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: AccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetGroups(
      groups: Seq[AccessGroup]
    ): CallHandler2[Arn, ExecutionContext, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsService
        .getAllGroups(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future.successful(groups))

    def mockAccessGroupsServiceGetGroupsWithException(
      ex: Exception
    ): CallHandler2[Arn, ExecutionContext, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsService
        .getAllGroups(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetGroupById(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler1[String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsService
        .getById(_: String))
        .expects(*)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsServiceGetGroupByIdWithException(
      ex: Exception
    ): CallHandler1[String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsService
        .getById(_: String))
        .expects(*)
        .returning(Future failed ex)

    def mockAccessGroupsServiceGetGroup(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler2[GroupId, ExecutionContext, Future[Option[AccessGroup]]] =
      (mockAccessGroupsService
        .get(_: GroupId)(_: ExecutionContext))
        .expects(GroupId(arn, groupName), *)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsServiceGetGroupWithException(
      ex: Exception
    ): CallHandler2[GroupId, ExecutionContext, Future[Option[AccessGroup]]] =
      (mockAccessGroupsService
        .get(_: GroupId)(_: ExecutionContext))
        .expects(GroupId(arn, groupName), *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceUpdate(
      accessGroupUpdateStatus: AccessGroupUpdateStatus
    ): CallHandler5[GroupId, AccessGroup, AgentUser, HeaderCarrier, ExecutionContext, Future[AccessGroupUpdateStatus]] =
      (mockAccessGroupsService
        .update(_: GroupId, _: AccessGroup, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(accessGroupUpdateStatus))

    def mockAccessGroupsServiceDelete(
      accessGroupDeletionStatus: AccessGroupDeletionStatus
    ): CallHandler3[GroupId, HeaderCarrier, ExecutionContext, Future[AccessGroupDeletionStatus]] =
      (mockAccessGroupsService
        .delete(_: GroupId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(accessGroupDeletionStatus))

    def mockAccessGroupsServiceDeleteWithException(
      ex: Exception
    ): CallHandler3[GroupId, HeaderCarrier, ExecutionContext, Future[AccessGroupDeletionStatus]] =
      (mockAccessGroupsService
        .delete(_: GroupId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetUnassignedClients(
      unassignedClients: Set[Client]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Set[Client]]] =
      (mockAccessGroupsService
        .getUnassignedClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful unassignedClients)

  }

  def jsonPayloadForCreateGroup(groupName: String): JsValue =
    Json.parse(s"""{
                  |    "groupName": "$groupName",
                  |    "clients": ${Json.toJson(Seq(enrolment1, enrolment2))},
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
                  |            "service": "HMRC-PPT-ORG",
                  |            "state": "Activated",
                  |            "friendlyName": "Frank Wright",
                  |            "identifiers": [
                  |                {
                  |                    "key": "EtmpRegistrationNumber",
                  |                    "value": "XAPPT0000012345"
                  |                }
                  |            ]
                  |        }
                  |    ]
                  |}""".stripMargin)

  def baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(CONTENTTYPE_APPLICATIONJSON)
}
