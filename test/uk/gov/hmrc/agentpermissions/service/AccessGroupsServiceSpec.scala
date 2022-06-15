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

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler4}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Enrolment, Identifier}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, RecordInserted, RecordUpdated, UpsertType}

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
    val enrolment1: Enrolment =
      Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))
    val enrolment2: Enrolment = Enrolment(
      "HMRC-PPT-ORG",
      "Activated",
      "Frank Wright",
      Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
    )
    val enrolment3: Enrolment =
      Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))

    val groupId: GroupId = GroupId(arn, groupName)
    val renamedGroupName = "renamedGroupName"

    val accessGroup: AccessGroup = AccessGroup(
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      Some(Set(enrolment1, enrolment2, enrolment3))
    )

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]

    val accessGroupsService: AccessGroupsService = new AccessGroupsServiceImpl(mockAccessGroupsRepository)

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockAccessGroupsRepositoryGet(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler2[Arn, String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryGetAll(
      accessGroups: Seq[AccessGroup]
    ): CallHandler1[Arn, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockAccessGroupsRepositoryInsert(
      maybeCreationId: Option[String]
    ): CallHandler1[AccessGroup, Future[Option[String]]] = (mockAccessGroupsRepository
      .insert(_: AccessGroup))
      .expects(accessGroup)
      .returning(Future.successful(maybeCreationId))

    def mockAccessGroupsRepositoryRenameGroup(
      maybeUpsertType: Option[UpsertType]
    ): CallHandler4[Arn, String, String, AgentUser, Future[Option[UpsertType]]] =
      (mockAccessGroupsRepository
        .renameGroup(_: Arn, _: String, _: String, _: AgentUser))
        .expects(arn, groupName, renamedGroupName, user)
        .returning(Future.successful(maybeUpsertType))

    def mockAccessGroupsRepositoryDelete(
      maybeDeletedCount: Option[Long]
    ): CallHandler2[Arn, String, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .delete(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeDeletedCount))

    def mockAccessGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ) =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: AccessGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))
  }

  "Calling create" when {

    "group of that name already exists" should {
      s"return $AccessGroupExistsForCreation" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroup))

        accessGroupsService
          .create(accessGroup)
          .futureValue shouldBe AccessGroupExistsForCreation
      }
    }

    "group of that name does not already exist" when {

      "insert calls returns nothing" should {
        s"return $AccessGroupNotCreated" in new TestScope {
          mockAccessGroupsRepositoryGet(None)
          mockAccessGroupsRepositoryInsert(None)

          accessGroupsService
            .create(accessGroup)
            .futureValue shouldBe AccessGroupNotCreated
        }
      }

      s"insert calls returns an id" should {
        s"return $AccessGroupCreated" in new TestScope {
          mockAccessGroupsRepositoryGet(None)
          mockAccessGroupsRepositoryInsert(Some(insertedId))

          accessGroupsService
            .create(accessGroup)
            .futureValue shouldBe AccessGroupCreated("KARN1234567%7Esome+group")
        }
      }
    }
  }

  "Calling group summaries" when {

    "access groups exist" should {
      "return corresponding summaries" in new TestScope {
        mockAccessGroupsRepositoryGetAll(Seq(accessGroup))

        accessGroupsService.groupSummaries(arn).futureValue shouldBe
          Seq(AccessGroupSummary("KARN1234567%7Esome+group", "some group", 3, 3))
      }
    }
  }

  "Fetching group" when {

    "group exists" should {
      "return corresponding summaries" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroup))

        accessGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(accessGroup)
      }
    }
  }

  "Renaming group" when {

    "group of that Arn and name does not exist" should {
      s"return $AccessGroupNotExistsForRenaming" in new TestScope {
        mockAccessGroupsRepositoryGet(None)

        accessGroupsService.rename(groupId, renamedGroupName, user).futureValue shouldBe AccessGroupNotExistsForRenaming
      }
    }

    "group of that Arn and name exists" when {

      "DB call to rename group returns nothing" should {
        s"return $AccessGroupNotRenamed" in new TestScope {
          mockAccessGroupsRepositoryGet(Some(accessGroup))
          mockAccessGroupsRepositoryRenameGroup(None)

          accessGroupsService.rename(groupId, renamedGroupName, user).futureValue shouldBe AccessGroupNotRenamed
        }
      }

      "DB call to rename group returns a value" when {

        s"DB call to rename group returns $RecordInserted" should {
          s"return $AccessGroupNotRenamed" in new TestScope {
            mockAccessGroupsRepositoryGet(Some(accessGroup))
            mockAccessGroupsRepositoryRenameGroup(Some(RecordInserted(insertedId)))

            accessGroupsService.rename(groupId, renamedGroupName, user).futureValue shouldBe AccessGroupNotRenamed
          }
        }

        s"DB call to rename group returns $RecordUpdated" should {
          s"return $AccessGroupRenamed" in new TestScope {
            mockAccessGroupsRepositoryGet(Some(accessGroup))
            mockAccessGroupsRepositoryRenameGroup(Some(RecordUpdated))

            accessGroupsService.rename(groupId, renamedGroupName, user).futureValue shouldBe AccessGroupRenamed
          }
        }

      }

    }
  }

  "Deleting group" when {

    "DB call to delete group returns nothing" should {
      s"return $AccessGroupNotDeleted" in new TestScope {
        mockAccessGroupsRepositoryDelete(None)

        accessGroupsService.delete(groupId).futureValue shouldBe AccessGroupNotDeleted
      }
    }

    "DB call to delete group returns some value" when {

      "DB call to delete group indicates one record was deleted" should {
        s"return $AccessGroupDeleted" in new TestScope {
          mockAccessGroupsRepositoryDelete(Some(1L))

          accessGroupsService.delete(groupId).futureValue shouldBe AccessGroupDeleted
        }
      }

      "DB call to delete group indicates no record was deleted" should {
        s"return $AccessGroupNotDeleted" in new TestScope {
          mockAccessGroupsRepositoryDelete(Some(0L))

          accessGroupsService.delete(groupId).futureValue shouldBe AccessGroupNotDeleted
        }
      }

    }

    "Updating group" when {

      "DB call to update group returns nothing" should {
        s"return $AccessGroupNotUpdated" in new TestScope {
          mockAccessGroupsRepositoryUpdate(None)

          accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
        }
      }

      "DB call to update group indicates one record was updated" should {
        s"return $AccessGroupUpdated" in new TestScope {
          mockAccessGroupsRepositoryUpdate(Some(1))

          accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupUpdated
        }
      }

      "DB call to update group indicates no record was updated" should {
        s"return $AccessGroupNotUpdated" in new TestScope {
          mockAccessGroupsRepositoryUpdate(Some(0))

          accessGroupsService.update(groupId, accessGroup, user).futureValue shouldBe AccessGroupNotUpdated
        }
      }
    }
  }

}