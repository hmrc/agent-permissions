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
import org.scalamock.handlers.CallHandler2
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Enrolment, Identifier}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.service.{AccessGroupCreated, AccessGroupCreationStatus, AccessGroupExists, AccessGroupNotCreated, AccessGroupSummary, AccessGroupsService, GroupId}

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
  val gid = "KARN0762398%7Esome+group"
  val enrolment1: Enrolment =
    Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
  val enrolment2: Enrolment = Enrolment(
    "HMRC-PPT-ORG",
    "Activated",
    "Frank Wright",
    Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
  )

  def jsonPayload(groupName: String): JsValue =
    Json.parse(s"""{
                  |    "groupName": "$groupName",
                  |    "clients": ${Json.toJson(Seq(enrolment1, enrolment2))},
                  |    "teamMembers": ${Json.toJson(Seq(user1, user2))},
                  |    "createdBy": {
                  |        "id": "${user.id}",
                  |        "name": "${user.name}"
                  |    }
                  |}""".stripMargin)

  def baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(CONTENTTYPE_APPLICATIONJSON)

  trait TestScope {

    val accessGroup: AccessGroup = AccessGroup(arn, groupName, now, now, user, user, Some(Set.empty), Some(Set.empty))
    val mockAccessGroupsService: AccessGroupsService = mock[AccessGroupsService]
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val actorSystem: ActorSystem = ActorSystem()

    val controller = new AccessGroupsController(mockAccessGroupsService)

    def mockAccessGroupsServiceCreate(
      accessGroupCreationStatus: AccessGroupCreationStatus
    ): CallHandler2[AccessGroup, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: AccessGroup)(_: ExecutionContext))
        .expects(*, *)
        .returning(Future.successful(accessGroupCreationStatus))

    def mockAccessGroupsServiceCreateWithException(
      ex: Exception
    ): CallHandler2[AccessGroup, ExecutionContext, Future[AccessGroupCreationStatus]] =
      (mockAccessGroupsService
        .create(_: AccessGroup)(_: ExecutionContext))
        .expects(*, *)
        .returning(Future.failed(ex))

    def mockAccessGroupsServiceGetGroupSummaries(
      groupSummaries: Seq[AccessGroupSummary]
    ): CallHandler2[Arn, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockAccessGroupsService
        .groupSummaries(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future.successful(groupSummaries))

    def mockAccessGroupsServiceGetGroupSummariesWithException(
      ex: Exception
    ): CallHandler2[Arn, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockAccessGroupsService
        .groupSummaries(_: Arn)(_: ExecutionContext))
        .expects(arn, *)
        .returning(Future.failed(ex))

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

  }

  "Call to create access group" when {

    "request does not contain json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        implicit val request = baseRequest

        val result = controller.createGroup(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains incorrect json payload" should {
      s"return $BAD_REQUEST" in new TestScope {
        implicit val request = baseRequest.withBody(JsString(""))

        val result = controller.createGroup(arn)(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "request contains correct json payload" when {

      implicit val request = baseRequest.withBody(jsonPayload(groupName))

      "provided arn is not valid" should {
        s"return $BAD_REQUEST" in new TestScope {
          val invalidArn: Arn = Arn("hello")

          val result = controller.createGroup(invalidArn)(request)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "provided arn is valid" when {

        "provided group name length is more than the maximum allowed" should {
          s"return $BAD_REQUEST" in new TestScope {
            val result = controller.createGroup(arn)(
              baseRequest
                .withBody(jsonPayload("0123456789012345678901234567890123"))
            )

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is less than the maximum allowed" when {

          s"access groups service returns $AccessGroupExists" should {
            s"return $CONFLICT" in new TestScope {
              mockAccessGroupsServiceCreate(AccessGroupExists)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CONFLICT
            }
          }

          s"access groups service returns $AccessGroupExists" should {
            s"return $CREATED" in new TestScope {
              mockAccessGroupsServiceCreate(AccessGroupCreated(createdId))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CREATED
            }
          }

          s"access groups service returns $AccessGroupExists" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
              mockAccessGroupsServiceCreate(AccessGroupNotCreated)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe INTERNAL_SERVER_ERROR
            }
          }

          s"access groups service throws an exception" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
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

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        val result = controller.groupsInformation(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "call to fetch access groups returns empty collection" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAccessGroupsServiceGetGroupSummaries(Seq.empty)

          val result = controller.groupsInformation(arn)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch access groups returns non-empty collection" should {
        s"return $OK" in new TestScope {

          mockAccessGroupsServiceGetGroupSummaries(
            Seq(AccessGroupSummary(gid, "some group", 3, 3))
          )

          val result = controller.groupsInformation(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result) shouldBe Json.parse(
            s"""[{"groupId":"$gid","groupName":"$groupName","clientCount":3,"teamMemberCount":3}]"""
          )
        }
      }

      "call to fetch access groups throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAccessGroupsServiceGetGroupSummariesWithException(new RuntimeException("boo boo"))

          val result = controller.groupsInformation(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  "Call to fetch group" when {

    "group id is not in the expected format" should {
      s"return $BAD_REQUEST" in new TestScope {
        val result = controller.getGroup("bad")(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "group id is in the expected format" when {

      "call to fetch group details returns nothing" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAccessGroupsServiceGetGroup(None)

          val result = controller.getGroup(gid)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch group details returns an access group" should {
        s"return $OK" in new TestScope {
          mockAccessGroupsServiceGetGroup(Some(accessGroup))

          val result = controller.getGroup(gid)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result) shouldBe Json.parse(
            s"""{"arn":"${arn.value}","groupName":"$groupName","created":"$now","lastUpdated":"$now","createdBy":{"id":"userId","name":"userName"},"lastUpdatedBy":{"id":"userId","name":"userName"},"teamMembers":[],"clients":[]}"""
          )
        }
      }

      "call to fetch group details throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAccessGroupsServiceGetGroupWithException(new RuntimeException("boo boo"))

          val result = controller.getGroup(gid)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }
}
