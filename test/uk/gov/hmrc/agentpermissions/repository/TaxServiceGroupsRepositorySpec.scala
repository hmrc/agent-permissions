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

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.result.UpdateResult
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.storagemodel.SensitiveTaxGroup
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, TaxGroup}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class TaxServiceGroupsRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[SensitiveTaxGroup] {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val dbId: UUID = GroupId.random()
    val groupName: String = "Some Group".toLowerCase
    val agent: AgentUser = AgentUser("userId", "userName")
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")

    val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
    val client2: Client = Client("HMRC-MTD-VAT~VRN~101746700", "Ann Von-Innes")

    val accessGroup: TaxGroup =
      TaxGroup(
        dbId,
        arn,
        groupName,
        now,
        now,
        agent,
        agent,
        Set(agent, user1, user2),
        "HMRC-MTD-VAT",
        automaticUpdates = false,
        Set(client1, client2)
      )

    def now: LocalDateTime = LocalDateTime.now()

    val groupsRepositoryImpl: TaxGroupsRepositoryV2Impl = repository.asInstanceOf[TaxGroupsRepositoryV2Impl]
    // trying to use trait interface as much as possible
    val groupsRepository: TaxGroupsRepositoryV2 = groupsRepositoryImpl
  }

  "TaxServiceGroupsRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        groupsRepositoryImpl.indexes.size shouldBe 2
        val collectionIndexes: Seq[IndexModel] = groupsRepositoryImpl.indexes

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
          groupsRepository.get(arn).futureValue shouldBe empty
        }
      }
    }

    "getting one group of Id" when {

      "group of that name does not exist" should {
        "return nothing" in new TestScope {
          groupsRepository.findById(dbId).futureValue shouldBe None
        }
      }
    }

    "getting one group by arn and service Id" when {

      "group of that service does not exist" should {
        "return nothing" in new TestScope {
          groupsRepository.getByService(arn, serviceCgt).futureValue shouldBe None
        }
      }
    }

    "getting one group of Arn" when {

      "group of that name does not exist" should {
        "return nothing" in new TestScope {
          groupsRepository.get(arn, groupName).futureValue shouldBe None
        }
      }
    }

    "inserting" when {

      "inserting a non-existing access group" should {
        s"return an id" in new TestScope {
          groupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
        }
        s"store the access group with field-level-encryption" in new TestScope {
          groupsRepository.insert(accessGroup).futureValue
          // checking at the raw Document level that the relevant fields have been encrypted
          val document = groupsRepositoryImpl.collection.find[Document]().collect().toFuture().futureValue
          document.toString should include(accessGroup.groupName) // the group name should be in plaintext
          // But the agent user ids and names should be encrypted
          (accessGroup.teamMembers ++ Seq(accessGroup.createdBy, accessGroup.lastUpdatedBy)).foreach { agentUser =>
            document.toString should not include agentUser.id
            document.toString should not include agentUser.name
          }
        }
      }

      "inserting an existing access group" should {
        s"return None" in new TestScope {
          groupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
          groupsRepository.insert(accessGroup).futureValue shouldBe None
        }
      }

      "inserting an access group with a group name differing only in case-sensitiveness" should {
        s"return None" in new TestScope {
          groupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          val accessGroupHavingGroupNameOfDifferentCase: TaxGroup =
            accessGroup.copy(groupName = accessGroup.groupName.toUpperCase)

          groupsRepository.insert(accessGroupHavingGroupNameOfDifferentCase).futureValue shouldBe None
        }
      }
    }

    "deleting group" when {

      "access group corresponding to group name provided does not exist in DB" should {
        s"indicate the correct deletion count" in new TestScope {
          groupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(0L)
        }
      }

      "group name provided is different than that existing in DB only case-sensitively" should {
        s"indicate the correct deletion count" in new TestScope {
          groupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]

          groupsRepository.delete(arn, groupName.toUpperCase).futureValue shouldBe Some(1L)
        }
      }
    }

    "updating group" when {

      "access group corresponding to group name provided does not exist in DB" should {
        "indicate the correct update count" in new TestScope {
          groupsRepository.update(arn, groupName, accessGroup).futureValue shouldBe Some(0)
        }
      }

    }

    "adding a team member" when {

      "group exists and can be added" should {
        "return the group" in new TestScope {
          // given
          groupsRepository.insert(accessGroup).futureValue.get shouldBe a[String]
          val agentToAdd: AgentUser = AgentUser("user10", "Bob Smith")

          // when
          val updateResult: UpdateResult = groupsRepository.addTeamMember(dbId, agentToAdd).futureValue

          // then
          updateResult.getModifiedCount shouldBe 1

          // and
          val updatedGroup: Option[TaxGroup] = groupsRepository.findById(dbId).futureValue
          updatedGroup.get.teamMembers.contains(agentToAdd) shouldBe true
          updatedGroup.get.teamMembers.size shouldBe 4

        }
      }

    }

    "groupExistsForTaxService" when {
      "access group exists for HMRC-TERS-ORG" should {
        "return true when asked for HMRC-TERS-ORG" in new TestScope {
          groupsRepository.insert(accessGroup.copy(service = "HMRC-TERS")).futureValue

          groupsRepository.groupExistsForTaxService(arn, "HMRC-TERS-ORG").futureValue shouldBe true
        }
        "return true when asked for HMRC-TERSNT-ORG" in new TestScope {
          groupsRepository.insert(accessGroup.copy(service = "HMRC-TERS")).futureValue

          groupsRepository.groupExistsForTaxService(arn, "HMRC-TERSNT-ORG").futureValue shouldBe true
        }
      }
    }
  }

  override protected def repository: PlayMongoRepository[SensitiveTaxGroup] =
    new TaxGroupsRepositoryV2Impl(mongoComponent, aesCrypto)
}
