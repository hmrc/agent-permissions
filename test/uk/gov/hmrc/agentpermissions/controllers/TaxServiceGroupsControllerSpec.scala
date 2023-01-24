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
import org.scalamock.handlers.{CallHandler3, CallHandler4, CallHandler5}
import play.api.libs.json.{JsArray, JsBoolean, JsString, JsValue, Json}
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

class TaxServiceGroupsControllerSpec extends BaseSpec {

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
  val clientVat2: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~000012345", "Frank Wright")

  trait TestScope {

    val taxServiceGroup: TaxServiceAccessGroup =
      TaxServiceAccessGroup(
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
    val mockTaxGroupsService: TaxGroupsService = mock[TaxGroupsService]
    implicit val mockAuthAction: AuthAction = mock[AuthAction]
    implicit val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val actorSystem: ActorSystem = ActorSystem()

    val controller = new TaxServiceGroupsController(mockTaxGroupsService)

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

    def mockTaxGroupsServiceCreate(
      groupCreationStatus: TaxServiceGroupCreationStatus
    ): CallHandler3[TaxServiceAccessGroup, HeaderCarrier, ExecutionContext, Future[TaxServiceGroupCreationStatus]] =
      (mockTaxGroupsService
        .create(_: TaxServiceAccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(groupCreationStatus))

    def mockTaxGroupsServiceCreateWithException(
      ex: Exception
    ): CallHandler3[TaxServiceAccessGroup, HeaderCarrier, ExecutionContext, Future[TaxServiceGroupCreationStatus]] =
      (mockTaxGroupsService
        .create(_: TaxServiceAccessGroup)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockTaxGroupsServiceGetGroups(
      groups: Seq[TaxServiceAccessGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .getAllTaxServiceGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockTaxGroupsServiceGetGroupsWithException(
      ex: Exception
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .getAllTaxServiceGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.failed(ex))

    def mockTaxGroupsServiceGetGroupById(
      maybeGroup: Option[TaxServiceAccessGroup]
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .getById(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.successful(maybeGroup))

    def mockTaxGroupsServiceGetGroupByIdWithException(
      ex: Exception
    ): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .getById(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future failed ex)

    def mockTaxGroupsServiceGetGroupByService(
      service: String,
      maybeGroup: Option[TaxServiceAccessGroup]
    ): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .get(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, service, *, *)
        .returning(Future.successful(maybeGroup))

    def mockTaxGroupsServiceGetGroupByServiceWithException(
      ex: Exception
    ): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .get(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, serviceVat, *, *)
        .returning(Future.failed(ex))

    def mockTaxGroupsServiceUpdate(
      groupUpdateStatus: TaxServiceGroupUpdateStatus
    ): CallHandler5[GroupId, TaxServiceAccessGroup, AgentUser, HeaderCarrier, ExecutionContext, Future[
      TaxServiceGroupUpdateStatus
    ]] =
      (mockTaxGroupsService
        .update(_: GroupId, _: TaxServiceAccessGroup, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returning(Future.successful(groupUpdateStatus))

    def mockTaxGroupsServiceDelete(
      groupDeletionStatus: TaxServiceGroupDeletionStatus
    ): CallHandler4[GroupId, AgentUser, HeaderCarrier, ExecutionContext, Future[TaxServiceGroupDeletionStatus]] =
      (mockTaxGroupsService
        .delete(_: GroupId, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.successful(groupDeletionStatus))

    def mockTaxGroupsServiceDeleteWithException(
      ex: Exception
    ): CallHandler4[GroupId, AgentUser, HeaderCarrier, ExecutionContext, Future[TaxServiceGroupDeletionStatus]] =
      (mockTaxGroupsService
        .delete(_: GroupId, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(Future.failed(ex))

    def mockGetClientCountForTaxGroups(
      count: Map[String, Int]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Map[String, Int]]] =
      (mockTaxGroupsService
        .clientCountForTaxGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(count))

    def mockGetClientCountForAvailableTaxServices(
      count: Map[String, Int]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Map[String, Int]]] =
      (mockTaxGroupsService
        .clientCountForAvailableTaxServices(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(count))

  }

  "Call to create tax service group" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.createGroup(arn)(baseRequest.withBody(jsonPayloadForCreateGroup(groupName)))
        status(result) shouldBe FORBIDDEN
      }
    }

    "auth throws exception" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgentWithException(InvalidBearerToken("auth failed"))

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

          s"tax service groups service returns $TaxServiceGroupExistsForCreation" should {
            s"return $CONFLICT" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceCreate(TaxServiceGroupExistsForCreation)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CONFLICT
            }
          }

          s"access groups service returns $TaxServiceGroupCreated" should {
            s"return $CREATED" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceCreate(TaxServiceGroupCreated(createdId))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe CREATED
            }
          }

          s"access groups service returns $TaxServiceGroupNotCreated" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceCreate(TaxServiceGroupNotCreated)

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe INTERNAL_SERVER_ERROR
            }
          }

          s"access groups service throws an exception" should {
            s"return $INTERNAL_SERVER_ERROR" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceCreateWithException(new RuntimeException("boo boo"))

              val result = controller.createGroup(arn)(request)

              status(result) shouldBe INTERNAL_SERVER_ERROR
            }
          }

        }
      }

    }
  }

  "Call to get client count for available tax services" when {
    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.clientCountForAvailableTaxServices(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.clientCountForAvailableTaxServices(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.clientCountForAvailableTaxServices(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch count returns empty map" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGetClientCountForAvailableTaxServices(Map.empty[String, Int])

          val result = controller.clientCountForAvailableTaxServices(arn)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "calls to fetch count returns map of count" should {
        s"return $OK" in new TestScope {
          val expectedCount = Map(
            "HMRC-MTD-VAT" -> 2,
            "HMRC-CGT-PD"  -> 3,
            "HMRC-PPT-ORG" -> 4,
            "HMRC-MTD-IT"  -> 5,
            "HMRC-TERS"    -> 7 // combined trusts
          )

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGetClientCountForAvailableTaxServices(expectedCount)

          val result = controller.clientCountForAvailableTaxServices(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[Map[String, Int]] shouldBe expectedCount
        }
      }

    }

  }

  "Call to get client count for existing tax groups" when {
    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.clientCountForTaxGroups(arn)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "provided arn is not valid" should {
      s"return $BAD_REQUEST" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

        val result = controller.clientCountForTaxGroups(invalidArn)(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "provided arn is valid" when {

      "provided arn does not match that identified by auth" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))

          val nonMatchingArn: Arn = Arn("FARN3782960")

          val result = controller.clientCountForTaxGroups(nonMatchingArn)(baseRequest)

          status(result) shouldBe BAD_REQUEST
        }
      }

      "calls to fetch count returns empty map" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGetClientCountForTaxGroups(Map.empty[String, Int])

          val result = controller.clientCountForTaxGroups(arn)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "calls to fetch count returns map of count" should {
        s"return $OK" in new TestScope {
          val expectedCount = Map(
            "HMRC-MTD-VAT" -> 2,
            "HMRC-CGT-PD"  -> 3,
            "HMRC-PPT-ORG" -> 4,
            "HMRC-MTD-IT"  -> 5,
            "HMRC-TERS"    -> 7 // combined trusts
          )

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockGetClientCountForTaxGroups(expectedCount)

          val result = controller.clientCountForTaxGroups(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[Map[String, Int]] shouldBe expectedCount
        }
      }

    }

  }

  "Call to fetch all tax service groups" when {

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
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroups(Seq.empty)

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe OK
        }
      }

      "calls to fetch groups returns data collections" should {

        s"return $OK" in new TestScope {

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroups(Seq(taxServiceGroup))

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe OK
          contentAsJson(result).as[Seq[AccessGroupSummary]] shouldBe Vector(
            AccessGroupSummary(
              taxServiceGroup._id.toHexString,
              taxServiceGroup.groupName,
              None,
              0,
              taxService = Some(taxServiceGroup.service)
            )
          )
        }
      }

      "call to fetch access groups throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupsWithException(new RuntimeException("boo boo"))

          val result = controller.groups(arn)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  "Call to fetch tax service group by id" when {

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
        mockTaxGroupsServiceGetGroupById(None)

        val result = controller.getGroup("bad")(baseRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "group id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided group id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      "call to fetch group details returns nothing" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(None)

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch group details returns a tax service group" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK

          val generatedJson: JsValue = contentAsJson(result)

          (generatedJson \ "arn").get shouldBe JsString(arn.value)
          (generatedJson \ "groupName").get shouldBe JsString(groupName)
          (generatedJson \ "createdBy" \ "id").get shouldBe JsString(user.id)
          (generatedJson \ "createdBy" \ "name").get shouldBe JsString(user.name)
          (generatedJson \ "teamMembers").get shouldBe JsArray(Seq.empty)
          (generatedJson \ "service").get shouldBe JsString(serviceVat)
          (generatedJson \ "automaticUpdates").get shouldBe JsBoolean(true)
          (generatedJson \ "excludedClients").get shouldBe JsArray(Seq.empty)
        }
      }

      "call to fetch group details throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupByIdWithException(new RuntimeException("boo boo"))

          val result = controller.getGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }

  "Call to fetch tax service group by ARN & service id" when {

    "authorised agent is not identified by auth" should {
      s"return $FORBIDDEN" in new TestScope {
        mockAuthActionGetAuthorisedAgent(None)

        val result = controller.getGroupByService(arn, serviceVat)(baseRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "service id is not in the expected format" should {
      s"return $NOT_FOUND" in new TestScope {
        mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
        mockTaxGroupsServiceGetGroupByService("bad", None)

        val result = controller.getGroupByService(arn, "bad")(baseRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "service id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided service id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockTaxGroupsServiceGetGroupByService(serviceVat, Some(taxServiceGroup))

          val result = controller.getGroupByService(arn, serviceVat)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      "call to fetch group details returns nothing" should {
        s"return $NOT_FOUND" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupByService(serviceVat, None)

          val result = controller.getGroupByService(arn, serviceVat)(baseRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "call to fetch group details returns a tax service group" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupByService(serviceVat, Some(taxServiceGroup))

          val result = controller.getGroupByService(arn, serviceVat)(baseRequest)

          status(result) shouldBe OK

          val generatedJson: JsValue = contentAsJson(result)

          (generatedJson \ "arn").get shouldBe JsString(arn.value)
          (generatedJson \ "groupName").get shouldBe JsString(groupName)
          (generatedJson \ "createdBy" \ "id").get shouldBe JsString(user.id)
          (generatedJson \ "createdBy" \ "name").get shouldBe JsString(user.name)
          (generatedJson \ "teamMembers").get shouldBe JsArray(Seq.empty)
          (generatedJson \ "service").get shouldBe JsString(serviceVat)
          (generatedJson \ "automaticUpdates").get shouldBe JsBoolean(true)
          (generatedJson \ "excludedClients").get shouldBe JsArray(Seq.empty)
        }
      }

      "call to fetch group details throws exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupByServiceWithException(new RuntimeException("boo boo"))

          val result = controller.getGroupByService(arn, serviceVat)(baseRequest)

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }

  }

  "Call to delete tax service group" when {

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
        mockTaxGroupsServiceGetGroupById(None)

        val result = controller.deleteGroup("bad")(baseRequest)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "group id is in the expected format" when {

      "auth identifies a different arn than that obtained from provided group id" should {
        s"return $FORBIDDEN" in new TestScope {
          val nonMatchingArn: Arn = Arn("FARN3782960")

          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe FORBIDDEN
        }
      }

      s"access groups service returns $TaxServiceGroupNotDeleted" should {
        s"return $NOT_MODIFIED" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))
          mockTaxGroupsServiceDelete(TaxServiceGroupNotDeleted)

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe NOT_MODIFIED
        }
      }

      s"access groups service returns $TaxServiceGroupDeleted" should {
        s"return $OK" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))
          mockTaxGroupsServiceDelete(TaxServiceGroupDeleted)

          val result = controller.deleteGroup(dbId.toHexString)(baseRequest)

          status(result) shouldBe OK
        }
      }

      s"access groups service throws an exception" should {
        s"return $INTERNAL_SERVER_ERROR" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))
          mockTaxGroupsServiceDeleteWithException(new RuntimeException("boo boo"))

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
          mockTaxGroupsServiceGetGroupById(None)

          val result = controller.updateGroup("bad")(request)
          status(result) shouldBe BAD_REQUEST
        }
      }

      "group id is in the expected format" when {

        "auth identifies a different arn than that obtained from provided group id" should {
          s"return $FORBIDDEN" in new TestScope {
            val nonMatchingArn: Arn = Arn("FARN3782960")

            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(nonMatchingArn, user)))
            mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))

            val result = controller.updateGroup(dbId.toHexString)(request)

            status(result) shouldBe FORBIDDEN
          }
        }

        "group for provided id does not exist" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockTaxGroupsServiceGetGroupById(None)

            val result = controller.updateGroup(dbId.toHexString)(request)

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is more than the maximum allowed" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))

            val result = controller.updateGroup(dbId.toHexString)(
              baseRequest
                .withBody(jsonPayloadForUpdatingGroup("0123456789012345678901234567890123456789012345678901"))
            )

            status(result) shouldBe BAD_REQUEST
          }
        }

        "provided group name length is less than the maximum allowed" when {

          s"access groups service returns $TaxServiceGroupNotUpdated" should {
            s"return $NOT_FOUND" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))
              mockTaxGroupsServiceUpdate(TaxServiceGroupNotUpdated)

              val result = controller.updateGroup(dbId.toHexString)(request)

              status(result) shouldBe NOT_FOUND
            }
          }

          s"access groups service returns $TaxServiceGroupUpdated" should {
            s"return $OK" in new TestScope {
              mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
              mockTaxGroupsServiceGetGroupById(Some(taxServiceGroup))
              mockTaxGroupsServiceUpdate(TaxServiceGroupUpdated)

              val result = controller.updateGroup(dbId.toHexString)(request)

              status(result) shouldBe OK
            }
          }

        }
      }
    }
  }

  "Call to add unassigned clients/team members to a tax service group" when {

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

      implicit val request = baseRequest.withBody(jsonPayloadForAddingMembersToAGroup())

      "group id is not in the expected format" should {
        s"return $BAD_REQUEST" in new TestScope {
          mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
          mockTaxGroupsServiceGetGroupById(None)

          val result = controller.addUnassignedMembers("bad")(request)
          status(result) shouldBe BAD_REQUEST
        }
      }

      "group id is in the expected format" when {

        s"access groups service returns $TaxServiceGroupUpdated" should {
          s"return $OK" in new TestScope {

            val group = taxServiceGroup.copy(
              teamMembers = Some(Set(AgentUser("1", "existing"))),
              excludedClients = Some(Set(Client("whatever", "friendly")))
            )
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockTaxGroupsServiceGetGroupById(Some(group))
            mockTaxGroupsServiceUpdate(TaxServiceGroupUpdated)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe OK
          }
        }

        s"access groups service returns $TaxServiceGroupNotUpdated" should {
          s"return $NOT_FOUND" in new TestScope {
            val group = taxServiceGroup.copy(
              teamMembers = Some(Set(AgentUser("1", "existing"))),
              excludedClients = Some(Set(Client("whatever", "friendly")))
            )
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockTaxGroupsServiceGetGroupById(Some(group))
            mockTaxGroupsServiceUpdate(TaxServiceGroupNotUpdated)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe NOT_FOUND
          }
        }

        "group for provided id does not exist" should {
          s"return $BAD_REQUEST" in new TestScope {
            mockAuthActionGetAuthorisedAgent(Some(AuthorisedAgent(arn, user)))
            mockTaxGroupsServiceGetGroupById(None)

            val result = controller.addUnassignedMembers(dbId.toHexString)(request)

            status(result) shouldBe BAD_REQUEST
          }
        }

      }
    }
  }

  def jsonPayloadForCreateGroup(groupName: String): JsValue =
    Json.parse(s"""{
                  |    "groupName": "$groupName",
                  |    "teamMembers": ${Json.toJson(Seq(user1, user2))},
                  |    "service": "$serviceVat",
                  |    "autoUpdate": ${JsBoolean(true)},
                  |    "excludedClients": ${Json.toJson(Seq(clientVat))}
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
                  |    "excludedClients": ${Json.toJson(Seq(clientVat, clientVat2))}
                  |}""".stripMargin)

  def jsonPayloadForAddingMembersToAGroup(): JsValue =
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
                  |    ]
                  |}""".stripMargin)

  def baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(CONTENTTYPE_APPLICATIONJSON)
}
