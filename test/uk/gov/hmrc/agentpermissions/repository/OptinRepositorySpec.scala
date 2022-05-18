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

class OptinRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[OptinRecord] {

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

    val optinRepository: OptinRepositoryImpl = repository.asInstanceOf[OptinRepositoryImpl]
  }

  "OptinRepository" when {

    "set up" should {
      "have correct indexes" in new TestScope {
        optinRepository.collectionName shouldBe "optin"
        optinRepository.indexes.size shouldBe 1
        val indexModel: IndexModel = optinRepository.indexes.head
        assert(indexModel.getKeys.toBsonDocument.containsKey("arn"))
        indexModel.getOptions.getName shouldBe "arnIdx"
        assert(indexModel.getOptions.isUnique)
      }
    }

    "fetching a non-existing record" should {
      "return nothing" in new TestScope {
        optinRepository.get(arn).futureValue shouldBe None
      }
    }

    "fetching an existing record" should {
      "return the optin record" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue shouldBe Some(RecordInserted)
        optinRepository.get(arn).futureValue shouldBe Some(optinRecord)
      }
    }

    "updating an existing record" should {
      s"return $RecordUpdated" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue shouldBe Some(RecordInserted)
        optinRepository.upsert(optinRecord).futureValue shouldBe Some(RecordUpdated)
      }
    }

  }

  override protected def repository: PlayMongoRepository[OptinRecord] = new OptinRepositoryImpl(mongoComponent)
}
