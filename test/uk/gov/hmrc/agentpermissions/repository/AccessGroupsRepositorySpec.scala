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
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.model.{Filters, IndexModel}
import org.mongodb.scala.result.UpdateResult
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.storagemodel.{SensitiveAgentUser, SensitiveClient, SensitiveCustomGroup}
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, CustomGroup}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class AccessGroupsRepositorySpec
    extends BaseSpec with PlayMongoRepositorySupport[SensitiveCustomGroup] with CleanMongoCollectionSupport {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(actorSystem)

  trait TestScope {
    val groupDbId: UUID = GroupId.random()
    val groupName: String = "Some Group".toLowerCase
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val user3: AgentUser = AgentUser("user3", "User 3")
    val sensitiveUser1: SensitiveAgentUser = SensitiveAgentUser(user1)
    val sensitiveUser2: SensitiveAgentUser = SensitiveAgentUser(user2)
    val sensitiveUser3: SensitiveAgentUser = SensitiveAgentUser(user3)

    val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
    val client2: Client = Client("HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345", "Frank Wright")
    val client3: Client = Client("HMRC-CGT-PD~CgtRef~XMCGTP123456789", "George Candy")
    val sensitiveClient1: SensitiveClient = SensitiveClient(client1)
    val sensitiveClient2: SensitiveClient = SensitiveClient(client2)
    val sensitiveClient3: SensitiveClient = SensitiveClient(client3)

    val accessGroup: CustomGroup =
      CustomGroup(
        id = groupDbId,
        arn = arn,
        groupName = groupName,
        created = now,
        lastUpdated = now,
        createdBy = user1,
        lastUpdatedBy = user1,
        teamMembers = Set(user1, user2, user3),
        clients = Set(client1, client2, client3)
      )

    def now: LocalDateTime = LocalDateTime.parse("2020-01-01T00:00:00.000")

    val accessGroupsRepository: CustomGroupsRepositoryV2Impl = repository.asInstanceOf[CustomGroupsRepositoryV2Impl]
  }

  "AccessGroupsRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        accessGroupsRepository.collectionName shouldBe "access-groups-custom"

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

      "a group exists for Arn" should {

        "return the group" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue
          accessGroupsRepository.get(arn).futureValue shouldBe Seq(accessGroup)
        }
      }
    }

    "getting one group of Id" when {

      "group of that name does not exist" should {

        "return nothing" in new TestScope {
          accessGroupsRepository.findById(groupDbId).futureValue shouldBe None
        }
      }

      "a group exists for Id" should {

        "return the group" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue
          accessGroupsRepository.findById(groupDbId).futureValue shouldBe Some(accessGroup)
        }
      }
    }

    "getting one group from Arn and group name" when {

      "group of that name and ARN does not exist" should {

        "return nothing" in new TestScope {
          accessGroupsRepository.get(arn, groupName).futureValue shouldBe None
        }
      }

      "group exists for the given ARN and name" should {

        "return the group" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue
          accessGroupsRepository.get(arn, groupName).futureValue shouldBe Some(accessGroup)
        }
      }
    }

    "inserting" when {

      "inserting a non-existing access group" should {

        "return an id" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
        }

        "store the access group with field-level encryption" in new TestScope {
          val id: String = accessGroupsRepository.insert(accessGroup).futureValue.get
          val sensitiveCustomGroup: SensitiveCustomGroup =
            accessGroupsRepository.collection.find(Filters.equal("_id", id)).toFuture().futureValue.head

          sensitiveCustomGroup shouldBe SensitiveCustomGroup(accessGroup)
        }
      }

      "inserting an existing access group" should {

        "return None" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
          accessGroupsRepository.insert(accessGroup).futureValue shouldBe None
        }
      }

      "inserting an access group with a group name differing only in case-sensitiveness" should {

        "return None" in new TestScope {
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          val accessGroupHavingGroupNameOfDifferentCase: CustomGroup =
            accessGroup.copy(groupName = accessGroup.groupName.toUpperCase)

          accessGroupsRepository.insert(accessGroupHavingGroupNameOfDifferentCase).futureValue shouldBe None
        }
      }
    }

    "deleting group" when {

      "access group corresponding to group name provided does not exist in DB" should {

        "indicate the correct deletion count" in new TestScope {
          accessGroupsRepository.delete(arn, groupName).futureValue shouldBe Some(0L)
        }
      }

      "the ARN and group name matches a record in the DB" should {

        "delete the record" when {

          "the provided group name matches exactly" in new TestScope {
            val id: String = accessGroupsRepository.insert(accessGroup).futureValue.get
            accessGroupsRepository.delete(arn, groupName).futureValue shouldBe Some(1L)
            accessGroupsRepository.findById(GroupId.fromString(id)).futureValue shouldBe None
          }

          "the provided group name is a different case" in new TestScope {
            val id: String = accessGroupsRepository.insert(accessGroup).futureValue.get
            accessGroupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(1L)
            accessGroupsRepository.findById(GroupId.fromString(id)).futureValue shouldBe None
          }
        }
      }
    }

    "updating group" when {

      "access group corresponding to group name provided does not exist in DB" should {

        "indicate the correct update count" in new TestScope {
          accessGroupsRepository.update(arn, groupName, accessGroup).futureValue shouldBe Some(0)
        }
      }

      "the ARN and group name matches a record in the DB" should {

        "update the record as expected" when {

          "the provided group name matches exactly" in new TestScope {
            val id: String = accessGroupsRepository.insert(accessGroup).futureValue.get
            val changedGroup: CustomGroup = accessGroup.copy(clients = Set())
            accessGroupsRepository.update(arn, groupName, changedGroup).futureValue shouldBe Some(1L)
            accessGroupsRepository.findById(GroupId.fromString(id)).futureValue shouldBe Some(changedGroup)
          }

          "the provided group name is a different case" in new TestScope {
            val id: String = accessGroupsRepository.insert(accessGroup).futureValue.get
            val changedGroup: CustomGroup = accessGroup.copy(clients = Set())
            accessGroupsRepository.update(arn, groupName.toUpperCase, changedGroup).futureValue shouldBe Some(1L)
            accessGroupsRepository.findById(GroupId.fromString(id)).futureValue shouldBe Some(changedGroup)
          }
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
            accessGroupsRepository.addTeamMember(groupDbId, agentToAdd).futureValue

          // then
          updateResult.getModifiedCount shouldBe 1

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId).futureValue
          updatedGroup.get.teamMembers.contains(agentToAdd) shouldBe true
          updatedGroup.get.teamMembers.size shouldBe 4
        }
      }

    }

    "removing a client from a group" when {

      "group exists" should {

        "return modified count of 1 when client is in group" in new TestScope {
          // given
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          accessGroup.clients.contains(client1) shouldBe true

          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(groupDbId, client1.enrolmentKey).futureValue

          // then
          updateResult.getModifiedCount shouldBe 1

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId).futureValue
          private val clients: Set[Client] = updatedGroup.get.clients
          clients.size shouldBe 2
          clients.contains(client1) shouldBe false
        }

        "return modified count of 0 when client is NOT in group" in new TestScope {
          // given
          accessGroupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(groupDbId, randomAlphabetic(23)).futureValue

          // then
          updateResult.getModifiedCount shouldBe 0

          // and
          val updatedGroup: Option[CustomGroup] = accessGroupsRepository.findById(groupDbId).futureValue
          private val clients: Set[Client] = updatedGroup.get.clients
          clients.size shouldBe 3
        }

        "return modified count of 0 when group is not found" in new TestScope {
          // when
          val updateResult: UpdateResult =
            accessGroupsRepository.removeClient(GroupId.random(), randomAlphabetic(23)).futureValue

          // then
          updateResult.getModifiedCount shouldBe 0

        }

        "test only deletion by arn" should {

          "delete the record matching the arn" in new TestScope {
            val arnToDelete: Arn = accessGroup.arn
            accessGroupsRepository.insert(accessGroup).futureValue
            val deletion: Long = accessGroupsRepository.delete(arnToDelete.value).futureValue
            accessGroupsRepository.get(arnToDelete, accessGroup.groupName).futureValue shouldBe None
            deletion shouldBe 1L
          }
        }
      }
    }
  }

  override protected lazy val repository: PlayMongoRepository[SensitiveCustomGroup] =
    new CustomGroupsRepositoryV2Impl(mongoComponent, aesCrypto)
}
