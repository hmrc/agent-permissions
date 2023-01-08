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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import scala.concurrent.ExecutionContext

class EacdSyncRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[EacdSyncRecord] {

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val eacdSyncRepository: EacdSyncRepository = repository.asInstanceOf[EacdSyncRepository]

  "Acquire" when {

    "entry does not exist for ARN" should {
      "return value" in new TestScope {
        eacdSyncRepository.acquire(arn, notBeforeSeconds = 10).futureValue.get.arn shouldBe arn
      }
    }

    "entry exists for ARN" when {

      "entry was last updated outside of refresh interval" should {
        "return value" in new TestScope {

          val refreshInterval = 10
          val lastUpdatedAt: Instant = Instant.now().minusSeconds(refreshInterval + 2)

          val eacdSyncRecord: EacdSyncRecord = (for {
            _                   <- repository.collection.insertOne(EacdSyncRecord(arn, lastUpdatedAt)).toFutureOption()
            maybeEacdSyncRecord <- eacdSyncRepository.acquire(arn, notBeforeSeconds = refreshInterval)
          } yield maybeEacdSyncRecord).futureValue.get

          eacdSyncRecord.arn shouldBe arn
          assert(eacdSyncRecord.updatedAt.isAfter(lastUpdatedAt))
        }
      }

      "entry was last updated within refresh interval" should {
        "return nothing" in new TestScope {

          val refreshInterval = 10
          val lastUpdatedAt: Instant = Instant.now().minusSeconds(refreshInterval - 2)

          (for {
            _                   <- repository.collection.insertOne(EacdSyncRecord(arn, lastUpdatedAt)).toFutureOption()
            maybeEacdSyncRecord <- eacdSyncRepository.acquire(arn, notBeforeSeconds = refreshInterval)
          } yield maybeEacdSyncRecord).futureValue shouldBe None
        }
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
  }

  override protected def repository: PlayMongoRepository[EacdSyncRecord] =
    new EacdSyncRepositoryImpl(mongoComponent)
}
