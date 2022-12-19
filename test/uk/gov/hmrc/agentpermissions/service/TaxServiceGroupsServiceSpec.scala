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

package uk.gov.hmrc.agentpermissions.service

import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class TaxServiceGroupsServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val groupName = "some group"
    val insertedId = "insertedId"
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val user3: AgentUser = AgentUser("user3", "User 3")

    val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
    val client2: Client = Client("HMRC-MTD-VAT~VRN~101746700", "Ann Von-Innes")

    val groupId: GroupId = GroupId(arn, groupName)
    val dbId: String = new ObjectId().toHexString

    val accessGroup: TaxServiceAccessGroup = TaxServiceAccessGroup(
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      "HMRC-MTD-VAT",
      automaticUpdates = true,
      Some(Set(client1, client2))
    )

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockTaxServiceGroupsRepository: TaxServiceGroupsRepository = mock[TaxServiceGroupsRepository]
    val mockAuditService: AuditService = mock[AuditService]

    val taxServiceGroupsService: TaxServiceGroupsService =
      new TaxServiceGroupsServiceImpl(
        mockTaxServiceGroupsRepository,
        mockAuditService
      )

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockTaxServiceGroupsRepositoryGet(
      maybeAccessGroup: Option[TaxServiceAccessGroup]
    ): CallHandler2[Arn, String, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxServiceGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxServiceGroupsRepositoryGetById(
      maybeAccessGroup: Option[TaxServiceAccessGroup]
    ): CallHandler1[String, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxServiceGroupsRepository
        .findById(_: String))
        .expects(dbId)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxServiceGroupsRepositoryGetAll(
      accessGroups: Seq[TaxServiceAccessGroup]
    ): CallHandler1[Arn, Future[Seq[TaxServiceAccessGroup]]] =
      (mockTaxServiceGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockTaxServiceGroupsRepositoryInsert(
      accessGroup: TaxServiceAccessGroup,
      maybeCreationId: Option[String]
    ): CallHandler1[TaxServiceAccessGroup, Future[Option[String]]] =
      (mockTaxServiceGroupsRepository
        .insert(_: TaxServiceAccessGroup))
        .expects(accessGroup)
        .returning(Future.successful(maybeCreationId))

    def mockTaxServiceGroupsRepositoryDelete(
      maybeDeletedCount: Option[Long]
    ): CallHandler2[Arn, String, Future[Option[Long]]] =
      (mockTaxServiceGroupsRepository
        .delete(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeDeletedCount))

    def mockTaxServiceGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, TaxServiceAccessGroup, Future[Option[Long]]] =
      (mockTaxServiceGroupsRepository
        .update(_: Arn, _: String, _: TaxServiceAccessGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

  }

  "Calling create" when {

    "group of that name already exists" should {
      s"return $TaxServiceGroupExistsForCreation" in new TestScope {
        mockTaxServiceGroupsRepositoryGet(Some(accessGroup))

        taxServiceGroupsService
          .create(accessGroup)
          .futureValue shouldBe TaxServiceGroupExistsForCreation
      }
    }

    "group of that name does not already exist" when {

      "insert calls returns nothing" should {
        s"return $TaxServiceGroupNotCreated" in new TestScope {
          mockTaxServiceGroupsRepositoryGet(None)

          mockTaxServiceGroupsRepositoryInsert(accessGroup, None)

          taxServiceGroupsService
            .create(accessGroup)
            .futureValue shouldBe TaxServiceGroupNotCreated
        }
      }

      s"insert calls returns an id" should {
        s"return $TaxServiceGroupCreated" in new TestScope {
          mockTaxServiceGroupsRepositoryGet(None)
          mockTaxServiceGroupsRepositoryInsert(accessGroup, Some(insertedId))

          taxServiceGroupsService
            .create(accessGroup)
            .futureValue shouldBe TaxServiceGroupCreated(insertedId)
        }
      }

    }
  }

  "Fetching all groups" when {

    "Tax Service groups exist" should {
      "return corresponding groups" in new TestScope {
        mockTaxServiceGroupsRepositoryGetAll(Seq(accessGroup))

        taxServiceGroupsService.getAllTaxServiceGroups(arn).futureValue shouldBe
          Seq(accessGroup)
      }
    }

    "no groups found" should {
      "return no Tax Service groups" in new TestScope {
        mockTaxServiceGroupsRepositoryGetAll(Seq.empty)

        taxServiceGroupsService.getAllTaxServiceGroups(arn).futureValue shouldBe
          Seq.empty
      }
    }
  }

  "Fetching group by arn and service id" when {

    "group exists" should {
      "return corresponding group" in new TestScope {
        mockTaxServiceGroupsRepositoryGet(Some(accessGroup))

        taxServiceGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup)
      }
    }

    "group does not exists" should {
      "return no group" in new TestScope {
        mockTaxServiceGroupsRepositoryGet(None)

        taxServiceGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          None
      }
    }

    "group with no team members" should {
      "return the group" in new TestScope {
        mockTaxServiceGroupsRepositoryGet(Some(accessGroup.copy(teamMembers = None)))

        taxServiceGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup.copy(teamMembers = None))
      }
    }
  }

  "Fetching group by id" when {

    "group exists" should {
      "return corresponding summaries" in new TestScope {
        mockTaxServiceGroupsRepositoryGetById(Some(accessGroup))

        taxServiceGroupsService.getById(dbId).futureValue shouldBe
          Some(accessGroup)
      }
    }
  }

  "Fetching groups for team member" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: TaxServiceAccessGroup = accessGroup
        val ag2: TaxServiceAccessGroup = accessGroup.copy(groupName = "group 2", teamMembers = Some(Set(user3)))

        mockTaxServiceGroupsRepositoryGetAll(Seq(ag1, ag2))

        taxServiceGroupsService.getTaxGroupSummariesForTeamMember(arn, "user3").futureValue shouldBe
          Seq(AccessGroupSummary(ag2._id.toHexString, "group 2", None, 1, isCustomGroup = false))
      }
    }
  }

  "Deleting group" when {

    "DB call to delete group returns nothing" should {
      s"return $TaxServiceGroupNotDeleted" in new TestScope {
        mockTaxServiceGroupsRepositoryDelete(None)

        taxServiceGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupNotDeleted
      }
    }

    "DB call to delete group returns some value" when {

      "call to delete group indicates no record was deleted" should {
        s"return $TaxServiceGroupNotDeleted" in new TestScope {
          mockTaxServiceGroupsRepositoryDelete(Some(0L))

          taxServiceGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupNotDeleted
        }
      }

      "call to delete group indicates one record was deleted" should {
        s"return $TaxServiceGroupDeleted" in new TestScope {
          mockTaxServiceGroupsRepositoryDelete(Some(1L))

          taxServiceGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupDeleted
        }
      }
    }

  }

  "Updating group" when {

    "DB call to update group returns nothing" should {
      s"return $TaxServiceGroupNotUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(None)

        taxServiceGroupsService.update(groupId, accessGroup, user).futureValue shouldBe TaxServiceGroupNotUpdated
      }
    }

    "DB call to update group indicates no record was updated" should {
      s"return $TaxServiceGroupNotUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(Some(0))

        taxServiceGroupsService.update(groupId, accessGroup, user).futureValue shouldBe TaxServiceGroupNotUpdated
      }
    }

    "DB call to update group indicates one record was updated" should {
      s"return $TaxServiceGroupUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(Some(1))

        taxServiceGroupsService.update(groupId, accessGroup, user).futureValue shouldBe TaxServiceGroupUpdated
      }
    }

  }

}
