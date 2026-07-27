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

import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.scalatest.OptionValues
import support.KeyRotationSupport
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.*
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.OptinEventType.*
import uk.gov.hmrc.agentpermissions.model.{Arn, SensitiveOptinRecord}
import uk.gov.hmrc.agentpermissions.repository.UpsertType.{RecordInserted, RecordUpdated}
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.{ObservableFuture, SingleObservableFuture}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class OptinRepositorySpec
    extends TestConstants with PlayMongoRepositorySupport[SensitiveOptinRecord] with CleanMongoCollectionSupport
    with OptionValues with KeyRotationSupport {

  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  given ActorSystem = ActorSystem()

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
        val document = optinRepositoryImpl.collection.find[Document]().collect().toFuture().futureValue.head
        val documentString = document.toString.replaceAll("\\s+", "")

        documentString should include(s"""status,BsonString{value='${optinRecord.status.value}'}""")

        // The agent user ids and names should be encrypted at rest.
        optinRecord.history.map(_.user).foreach { agentUser =>
          documentString should not include (agentUser.id)
          documentString should not include (agentUser.name)
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

    "fetching all" should {
      "return all" in new TestScope {
        optinRepository.upsert(optinRecord).futureValue

        optinRepository.getAll().futureValue shouldBe Seq(optinRecord)
      }
    }

    "test only deletion" should {
      "delete the record matching the arn" in new TestScope {
        val arnToDelete: Arn = optinRecord.arn
        optinRepository.upsert(optinRecord).futureValue
        val deletion: Long = optinRepository.delete(arnToDelete.value).futureValue
        optinRepository.get(arnToDelete).futureValue shouldBe None
        deletion shouldBe 1L
      }
    }

    "encryption key migration" should {
      "rotate the encryption key for records by reading them out and writing them back again" in new TestScope {
        def generateRecord =
          OptinRecord(
            Arn(Random.alphanumeric.take(10).mkString),
            List(OptinEvent(OptedIn, user, LocalDateTime.now()))
          )

        val testData = Seq.fill(20)(generateRecord)
        Future.traverse(testData)(optinRepository.upsert).futureValue

        // simulate a newly deployed encryption key
        KeyRotationCrypto.rotateSecretKey(to = "eu2IgkfpOI9MYvoG1Q3eoFsWzyHO8fw/bwaSdS+21zg=", keepPreviousKey = true)

        optinRepositoryImpl.migrate(batchSize = 5, deadline = patienceConfig.timeout.fromNow).futureValue

        // ensure all records were processed correctly
        val results = optinRepositoryImpl.collection.find().map(_.decryptedValue).toFuture().futureValue
        results should contain theSameElementsAs testData

        // the old encryption key can no longer decrypt the record
        KeyRotationCrypto.rotateSecretKey(to = DefaultSecretKey)
        optinRepositoryImpl.collection.find().toFuture().failed.futureValue shouldBe a[SecurityException]
      }
    }
  }

  override protected val repository: PlayMongoRepository[SensitiveOptinRecord] =
    new OptinRepositoryImpl(mongoComponent, KeyRotationCrypto)
}
