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
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.BetaInviteRecord
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import scala.concurrent.ExecutionContext

class BetaInviteRepositorySpec
    extends BaseSpec with PlayMongoRepositorySupport[BetaInviteRecord] with CleanMongoCollectionSupport {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val betaInviteRecord: BetaInviteRecord = BetaInviteRecord(
      arn,
      "userId",
      hideBetaInvite = true
    )

    val betaInviteRepoImpl: BetaInviteRepositoryImpl = repository.asInstanceOf[BetaInviteRepositoryImpl]
    val betaInviteRepository: BetaInviteRepository =
      betaInviteRepoImpl // trying to use trait interface as much as possible
  }

  "BetaInviteRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        betaInviteRepoImpl.collectionName shouldBe "beta-invite"
        betaInviteRepoImpl.indexes.size shouldBe 1
        val indexModel: IndexModel = betaInviteRepoImpl.indexes.head
        assert(indexModel.getKeys.toBsonDocument.containsKey("agentUserId"))
        indexModel.getOptions.getName shouldBe "agentUserIdx"
        assert(indexModel.getOptions.isUnique)
      }
    }

    "inserting a record" should {
      "store the record" in new TestScope {
        betaInviteRepository.upsert(betaInviteRecord).futureValue

        val document: Seq[Document] = betaInviteRepoImpl.collection.find[Document]().collect().toFuture().futureValue
        document.toString should include(
          betaInviteRecord.hideBetaInvite.toString
        )

      }
    }

    "fetching a non-existing record" should {
      "return nothing" in new TestScope {
        betaInviteRepository.get(user).futureValue shouldBe None
      }
    }

    "fetching an existing record" should {
      "return the optin record" in new TestScope {
        betaInviteRepository.upsert(betaInviteRecord).futureValue.get shouldBe a[RecordInserted]
        betaInviteRepository.get(user).futureValue shouldBe Some(betaInviteRecord)
      }
    }

    "update an existing record" should {
      s"return $RecordUpdated" in new TestScope {
        betaInviteRepository.upsert(betaInviteRecord).futureValue.get shouldBe a[RecordInserted]
        betaInviteRepository.upsert(betaInviteRecord).futureValue.get shouldBe RecordUpdated
      }
    }

  }

  override protected lazy val repository: PlayMongoRepository[BetaInviteRecord] =
    new BetaInviteRepositoryImpl(mongoComponent)
}
