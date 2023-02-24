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

package uk.gov.hmrc.agentpermissions.repository

import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.bson.types.ObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.result.UpdateResult
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.SensitiveAccessGroup
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class AccessGroupsRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[SensitiveAccessGroup] {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val groupDbId: ObjectId = new ObjectId()
    val groupName: String = "Some Group".toLowerCase
    val agent: AgentUser = AgentUser("userId", "userName")
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")

    val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
    val client2: Client = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
    val client3: Client = Client("HMRC-CGT-PD~CgtRef~XMCGTP123456789", "George Candy")

    val accessGroup: CustomGroup =
      CustomGroup(
        groupDbId,
        arn,
        groupName,
        now,
        now,
        agent,
        agent,
        teamMembers = Some(Set(agent, user1, user2)),
        clients = Some(Set(client1, client2, client3))
      )

    def now: LocalDateTime = LocalDateTime.now()

    val accessGroupsRepositoryImpl: AccessGroupsRepositoryImpl = repository.asInstanceOf[AccessGroupsRepositoryImpl]
    val accessGroupsRepository: AccessGroupsRepository =
      accessGroupsRepositoryImpl // trying to use trait interface as much as possible
  }

  "AccessGroupsRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        accessGroupsRepositoryImpl.collectionName shouldBe "access-groups"

        accessGroupsRepositoryImpl.indexes.size shouldBe 2
        val collectionIndexes: Seq[IndexModel] = accessGroupsRepositoryImpl.indexes

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

    "getting one group of Id" when {

      "group of that name does not exist" should {
        "return nothing" in new TestScope {
          accessGroupsRepository.findById(groupDbId.toHexString).futureValue shouldBe None
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

    "inserting" when {

      "inserting a non-existing access group" should {
        s"return an id" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
        }
        s"store the access group with field-level-encryption" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue
          // checking at the raw Document level that the relevant fields have been encrypted
          val document = accessGroupsRepositoryImpl.collection.find[Document]().collect().toFuture().futureValue
          document.toString should include(accessGroup.groupName) // the group name should be in plaintext
          // But the agent user ids and names should be encrypted
          (accessGroup.teamMembers.get ++ Seq(accessGroup.createdBy, accessGroup.lastUpdatedBy)).foreach { agentUser =>
            document.toString should not include agentUser.id
            document.toString should not include agentUser.name
          }
        }
      }

      "inserting an existing access group" should {
        s"return None" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
          accessGroupsRepository.insert(accessGroup).futureValue shouldBe None
        }
      }

      "inserting an access group with a group name differing only in case-sensitiveness" should {
        s"return None" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          val accessGroupHavingGroupNameOfDifferentCase: CustomGroup =
            accessGroup.copy(groupName = accessGroup.groupName.toUpperCase)

          accessGroupsRepository.insert(accessGroupHavingGroupNameOfDifferentCase).futureValue shouldBe None
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
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          accessGroupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(1L)
        }
      }
    }

    "updating group" when {

      "access group corresponding to group name provided does not exist in DB" should {
        "indicate the correct update count" in new TestScope {
          accessGroupsRepository.update(arn, groupName, accessGroup).futureValue shouldBe Some(0)
        }
      }

    }

    "adding a team member" when {

      "group exists and can be added" should {
        "return the group" in new TestScope {
          // given
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
          val agentToAdd: AgentUser = AgentUser("user10", "Bob Smith")

          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.addTeamMember(groupDbId.toString, agentToAdd).futureValue

          // then
          updateResult.getModifiedCount shouldBe 1

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId.toString).futureValue
          updatedGroup.get.teamMembers.isDefined shouldBe true
          updatedGroup.get.teamMembers.get.contains(agentToAdd) shouldBe true
          updatedGroup.get.teamMembers.get.size shouldBe 4
        }
      }

    }

    "removing a client from a group" when {

      "group exists" should {

        "return modified count of 1 when client is in group" in new TestScope {
          // given
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          accessGroup.clients.get.contains(client1) shouldBe true

          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(groupDbId.toString, client1.enrolmentKey).futureValue

          // then
          updateResult.getModifiedCount shouldBe 1

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId.toString).futureValue
          private val clients: Set[Client] = updatedGroup.get.clients.get
          clients.size shouldBe 2
          clients.contains(client1) shouldBe false
        }

        "return modified count of 0 when client is NOT in group" in new TestScope {
          // given
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(groupDbId.toString, randomAlphabetic(23)).futureValue

          // then
          updateResult.getModifiedCount shouldBe 0

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId.toString).futureValue
          private val clients: Set[Client] = updatedGroup.get.clients.get
          clients.size shouldBe 3
        }

        "return modified count of 0 when group is not found" in new TestScope {
          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(randomAlphabetic(5), randomAlphabetic(23)).futureValue

          // then
          updateResult.getModifiedCount shouldBe 0

        }
      }

    }
  }

  override protected def repository: PlayMongoRepository[SensitiveAccessGroup] =
    new AccessGroupsRepositoryImpl(mongoComponent, aesGcmCrypto)
}
