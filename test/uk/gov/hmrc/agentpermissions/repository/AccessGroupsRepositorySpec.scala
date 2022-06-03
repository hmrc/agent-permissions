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

package uk.gov.hmrc.agentpermissions.repository

import org.mongodb.scala.model.IndexModel
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class AccessGroupsRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[AccessGroup] {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "Some Group".toLowerCase
    val agent: AgentUser = AgentUser("userId", "userName")
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val renamedGroupName = "renamedGroupName"

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

    val accessGroup: AccessGroup =
      AccessGroup(
        arn,
        groupName,
        now,
        now,
        agent,
        agent,
        Some(Set(agent, user1, user2)),
        Some(Set(enrolment1, enrolment2, enrolment3))
      )

    def now: LocalDateTime = LocalDateTime.now()

    val accessGroupsRepository: AccessGroupsRepositoryImpl = repository.asInstanceOf[AccessGroupsRepositoryImpl]
  }

  "AccessGroupsRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        accessGroupsRepository.collectionName shouldBe "access-groups"

        accessGroupsRepository.indexes.size shouldBe 2
        val collectionIndexes: Seq[IndexModel] = accessGroupsRepository.indexes

        val arnIndexModel: IndexModel = collectionIndexes.head
        assert(arnIndexModel.getKeys.toBsonDocument.containsKey("arn"))
        arnIndexModel.getOptions.getName shouldBe "arnIdx"
        arnIndexModel.getOptions.isUnique shouldBe false

        val arnGroupNameIndexModel: IndexModel = collectionIndexes(1)
        assert(arnGroupNameIndexModel.getKeys.toBsonDocument.containsKey("arn"))
        assert(arnGroupNameIndexModel.getKeys.toBsonDocument.containsKey("groupName"))
        arnGroupNameIndexModel.getOptions.getName shouldBe "arnGroupNameIdx"
        arnGroupNameIndexModel.getOptions.isUnique shouldBe true

      }
    }

    "getting all groups of Arn" when {

      "no groups exist for Arn" should {
        "return nothing" in new TestScope {
          accessGroupsRepository.get(arn).futureValue shouldBe empty
        }
      }
    }

    "getting one group of Arn" when {

      "group of that name does not exist" should {
        "return nothing" in new TestScope {
          accessGroupsRepository.get(arn, groupName).futureValue shouldBe None
        }
      }
    }

    "upserting" when {

      "upserting a non-existing access group" should {
        s"return $RecordInserted" in new TestScope {
          accessGroupsRepository.upsert(accessGroup).futureValue.get shouldBe a[RecordInserted]
        }
      }

      "upserting an existing access group" should {
        s"return $RecordUpdated" in new TestScope {
          accessGroupsRepository.upsert(accessGroup).futureValue.get shouldBe a[RecordInserted]
          accessGroupsRepository.upsert(accessGroup).futureValue shouldBe Some(RecordUpdated)
        }
      }

      "upserting an access group with a group name differing only in case-sensitiveness" should {
        s"return $RecordUpdated" in new TestScope {
          accessGroupsRepository.upsert(accessGroup).futureValue.get shouldBe a[RecordInserted]

          val accessGroupHavingGroupNameOfDifferentCase: AccessGroup =
            accessGroup.copy(groupName = accessGroup.groupName.toUpperCase)

          accessGroupsRepository.upsert(accessGroupHavingGroupNameOfDifferentCase).futureValue shouldBe Some(
            RecordUpdated
          )
        }
      }
    }

    "renaming group" when {

      "group name provided is different than that existing in DB" should {
        s"return $RecordUpdated" in new TestScope {
          accessGroupsRepository.upsert(accessGroup).futureValue.get shouldBe a[RecordInserted]

          accessGroupsRepository.renameGroup(arn, groupName, renamedGroupName, agent).futureValue shouldBe Some(
            RecordUpdated
          )
        }
      }
    }

    "deleting group" when {

      "access group corresponding to group name provided does not exist in DB" should {
        s"indicate the correct deletion count" in new TestScope {
          accessGroupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(0L)
        }
      }

      "group name provided is different than that existing in DB only case-sensitively" should {
        s"indicate the correct deletion count" in new TestScope {
          accessGroupsRepository.upsert(accessGroup).futureValue.get shouldBe a[RecordInserted]

          accessGroupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(1L)
        }
      }
    }
  }

  override protected def repository: PlayMongoRepository[AccessGroup] = new AccessGroupsRepositoryImpl(mongoComponent)
}
