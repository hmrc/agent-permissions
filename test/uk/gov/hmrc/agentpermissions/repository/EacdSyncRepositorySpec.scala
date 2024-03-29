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
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import java.time.Instant
import scala.concurrent.ExecutionContext

class EacdSyncRepositorySpec
    extends BaseSpec with PlayMongoRepositorySupport[EacdSyncRecord] with CleanMongoCollectionSupport {

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val eacdSyncRepository: EacdSyncRepository = repository.asInstanceOf[EacdSyncRepository]

  "Acquire" when {

    "entry does not exist for ARN" should {
      "return value" in new TestScope {
        eacdSyncRepository.acquire(arn).futureValue.get.arn shouldBe arn
      }
    }

    "entry exists for ARN" when {

      "entry was last updated outside of refresh interval" should {
        "return value" in new TestScope {

          val refreshInterval = mockAppConfig.eacdSyncNotBeforeSeconds
          val lastUpdatedAt: Instant = Instant.now().minusSeconds(refreshInterval + 2)

          val eacdSyncRecord: EacdSyncRecord = (for {
            _                   <- repository.collection.insertOne(EacdSyncRecord(arn, lastUpdatedAt)).toFutureOption()
            maybeEacdSyncRecord <- eacdSyncRepository.acquire(arn)
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
            maybeEacdSyncRecord <- eacdSyncRepository.acquire(arn)
          } yield maybeEacdSyncRecord).futureValue shouldBe None
        }
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
  }

  // forced to do this as for some reason I can't get a functioning mock in this setup
  private def mockAppConfig = new AppConfig {
    override def agentUserClientDetailsBaseUrl: String = ""
    override def agentSizeMaxClientCountAllowed: Int = 1000
    override def checkArnAllowList: Boolean = false
    override def allowedArns: Seq[String] = Seq.empty
    override def clientsRemovalChunkSize: Int = 100
    override def teamMembersRemovalChunkSize: Int = 100
    override def accessGroupChunkSize: Int = 100
    override def useEnrolmentAssignmentsChunkSize: Int = 100
    override def eacdSyncNotBeforeSeconds: Int = 10 // <- The value we care about in this test
  }

  override protected lazy val repository: PlayMongoRepository[EacdSyncRecord] =
    new EacdSyncRepositoryImpl(mongoComponent, mockAppConfig)
}
