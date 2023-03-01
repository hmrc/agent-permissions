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

import org.bson.types.ObjectId
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class TestSensitiveRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[SensitiveFoo] {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait TestScope {
    val testSensitiveRepository: TestSensitiveRepository = repository.asInstanceOf[TestSensitiveRepository]
  }

  "AccessGroupsRepository" should {

    "set up" in new TestScope {
      testSensitiveRepository.collectionName shouldBe "test-sensitive"
    }

    val testThing1 = SensitiveFoo(
      new ObjectId(),
      "public",
      SearchableSensitiveString("restricted"),
      Seq(SensitiveBar("hello", SearchableSensitiveString("secret")))
    )
    val testThing2 = SensitiveFoo(
      new ObjectId(),
      "visible",
      SearchableSensitiveString("hidden"),
      Seq(
        SensitiveBar("clear", SearchableSensitiveString("enigma")),
        SensitiveBar("plain", SearchableSensitiveString("mystery"))
      )
    )

    "search by plain field" in new TestScope {
      updateIndexPreference(false).futureValue
      testSensitiveRepository
        .insert(testThing1)
        .futureValue
      testSensitiveRepository
        .insert(testThing2)
        .futureValue
      val result = testSensitiveRepository.findByClear(testThing1.fooPublic).futureValue
      result shouldBe Some(testThing1)
    }

    "search by crypted field" in new TestScope {
      updateIndexPreference(false).futureValue
      testSensitiveRepository
        .insert(testThing1)
        .futureValue
      testSensitiveRepository
        .insert(testThing2)
        .futureValue
      val result = testSensitiveRepository.findByCrypted(testThing1.fooSensitive).futureValue
      result shouldBe Some(testThing1)
    }

    "search by crypted field within array" in new TestScope {
      updateIndexPreference(false).futureValue
      testSensitiveRepository
        .insert(testThing1)
        .futureValue
      testSensitiveRepository
        .insert(testThing2)
        .futureValue
      val result =
        testSensitiveRepository.findBySensitiveFieldWithinArray(testThing2.bars.head.barSensitive).futureValue
      result shouldBe Some(testThing2)
    }
  }

  override protected def repository: PlayMongoRepository[SensitiveFoo] =
    new TestSensitiveRepository(mongoComponent, aesGcmCrypto)
}
