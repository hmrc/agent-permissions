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

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.SensitiveOptinRecord
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class OptinRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[SensitiveOptinRecord] {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val optinRecord: OptinRecord = OptinRecord(
      arn,
      List(
        OptinEvent(OptedIn, user, LocalDateTime.now())
      )
    )

    val optinRepositoryImpl: OptinRepositoryImpl = repository.asInstanceOf[OptinRepositoryImpl]
    val optinRepository: OptinRepository = optinRepositoryImpl // trying to use trait interface as much as possible
  }

  "OptinRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        optinRepositoryImpl.collectionName shouldBe "optin"
        optinRepositoryImpl.indexes.size shouldBe 1
        val indexModel: IndexModel = optinRepositoryImpl.indexes.head
        assert(indexModel.getKeys.toBsonDocument.containsKey("arn"))
        indexModel.getOptions.getName shouldBe "arnIdx"
        assert(indexModel.getOptions.isUnique)
      }
    }

    "inserting a record" should {
      "store the record with field-level encryption" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue
        // checking at the raw Document level that the relevant fields have been encrypted
        val document = optinRepositoryImpl.collection.find[Document]().collect().toFuture().futureValue
        document.toString should include(
          optinRecord.status.value
        ) // the opt-in/opt-out status string should be in plaintext
        // But the agent user ids and names should be encrypted
        optinRecord.history.map(_.user).foreach { agentUser =>
          document.toString should not include agentUser.id
          document.toString should not include agentUser.name
        }

      }
    }

    "fetching a non-existing record" should {
      "return nothing" in new TestScope {
        optinRepository.get(arn).futureValue shouldBe None
      }
    }

    "fetching an existing record" should {
      "return the optin record" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue.get shouldBe a[RecordInserted]
        optinRepository.get(arn).futureValue shouldBe Some(optinRecord)
      }
    }

    "updating an existing record" should {
      s"return $RecordUpdated" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue.get shouldBe a[RecordInserted]
        optinRepository.upsert(optinRecord).futureValue shouldBe Some(RecordUpdated)
      }
    }

  }

  override protected def repository: PlayMongoRepository[SensitiveOptinRecord] =
    new OptinRepositoryImpl(mongoComponent, aesGcmCrypto)
}
