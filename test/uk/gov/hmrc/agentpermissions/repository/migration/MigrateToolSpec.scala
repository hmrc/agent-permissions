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

package uk.gov.hmrc.agentpermissions.repository.migration

import org.mongodb.scala.model.Filters
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.IntegrationPatience
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.SensitiveOptinRecord
import uk.gov.hmrc.agentpermissions.repository._
import uk.gov.hmrc.agentpermissions.model.accessgroups.AgentUser
import uk.gov.hmrc.agentpermissions.model.accessgroups.optin.{OptedIn, OptinEvent, OptinRecord}
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.play.json.CollectionFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class MigrateToolSpec extends BaseSpec with CleanMongoCollectionSupport with MockFactory with IntegrationPatience {

  "migration functionality" should {

    // Note: This is simply a randomly-chosen secret key to run tests
    val someKey = "oxKL65sEH2MT6xBVXX3QFwZi+a8P1B/5"

    val aesCrypto = SymmetricCryptoFactory.aesCrypto(someKey)

    val jsObjectFormat: Format[JsObject] = new Format[JsObject] {
      override def writes(o: JsObject): JsValue = o
      override def reads(json: JsValue): JsResult[JsObject] = json match {
        case obj: JsObject => JsSuccess(obj)
        case other => throw new RuntimeException(s"reads fails because the json is not a JsObject: ${other.toString}")
      }
    }

    val oldCustomCollection =
      CollectionFactory.collection(mongoComponent.database, "access-groups", jsObjectFormat, Seq.empty)
    val oldTaxCollection =
      CollectionFactory.collection(mongoComponent.database, "tax-service-groups", jsObjectFormat, Seq.empty)
    val optInRepo = new OptinRepositoryImpl(mongoComponent, aesCrypto)

    val appConfigStub = stub[AppConfig]
    val syncRepo = new EacdSyncRepositoryImpl(mongoComponent, appConfigStub)

    val migrateFunctionality =
      new MigrateTool(
        mongoComponent,
        syncRepo,
        optInRepo,
        stub[Configuration]
      )

    "correctly delete legacy data" in {
      (() => appConfigStub.eacdSyncNotBeforeSeconds).when().returns(300)

      val optinRecord = OptinRecord(
        Arn("AARN0123456"),
        List(OptinEvent(OptedIn, AgentUser("userid", "name"), LocalDateTime.now()))
      )

      oldCustomCollection.insertOne(Json.obj("foo" -> JsString("baz"))).toFuture().futureValue
      oldTaxCollection.insertOne(Json.obj("foo" -> JsString("baz"))).toFuture().futureValue
      optInRepo.collection
        .insertMany(
          Seq(
            SensitiveOptinRecord(optinRecord),
            SensitiveOptinRecord(optinRecord.copy(arn = Arn("BACKUPAARN0123456")))
          )
        )
        .toFuture()
        .futureValue
      oldCustomCollection.countDocuments(Filters.empty()).toFuture().futureValue shouldBe 1L
      oldTaxCollection.countDocuments(Filters.empty()).toFuture().futureValue shouldBe 1L
      optInRepo.collection.countDocuments(Filters.empty()).toFuture().futureValue shouldBe 2L

      migrateFunctionality.doTheMigration().futureValue

      oldCustomCollection.countDocuments(Filters.empty()).toFuture().futureValue shouldBe 0L
      oldTaxCollection.countDocuments(Filters.empty()).toFuture().futureValue shouldBe 0L
      optInRepo.collection.countDocuments(Filters.equal("arn", "AARN0123456")).toFuture().futureValue shouldBe 1L
      optInRepo.collection.countDocuments(Filters.equal("arn", "BACKUPAARN0123456")).toFuture().futureValue shouldBe 0L
    }

    "fail if lock cannot be acquired" in {
      (() => appConfigStub.eacdSyncNotBeforeSeconds).when().returns(300)

      syncRepo
        .acquire(Arn("MIGRATELOCK"))
        .futureValue // Already acquire the lock so the migration cannot re-acquire it later
      migrateFunctionality.doTheMigration().failed.futureValue should matchPattern {
        case MigrationAbortedException(_, false) => // no cleanup
      }
    }

  }
}
