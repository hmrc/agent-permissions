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

package uk.gov.hmrc.agentpermissions.repository.legacy

import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.IntegrationPatience
import play.api.Configuration
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.model.SensitiveOptinRecord
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository._
import uk.gov.hmrc.agentpermissions.repository.storagemodel.{SensitiveCustomGroup, SensitiveTaxGroup}
import uk.gov.hmrc.agents.accessgroups.optin.{OptedIn, OptinEvent, OptinRecord}
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup}
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class MigrateToV2Spec extends BaseSpec with CleanMongoCollectionSupport with MockFactory with IntegrationPatience {

  "migration functionality" should {

    // Note: This is simply a randomly-chosen secret key to run tests
    val someOldKey = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x"
    val someNewKey = "oxKL65sEH2MT6xBVXX3QFwZi+a8P1B/5"

    val arn1 = Arn("KARN1234567")
    val arn2 = Arn("XARN0112233")
    val created1 = LocalDateTime.of(2023, 3, 30, 14, 15, 0)
    val created2 = LocalDateTime.of(2023, 3, 30, 14, 15, 30)
    val lastUpdated1 = LocalDateTime.of(2023, 3, 30, 14, 16, 0)
    val lastUpdated2 = LocalDateTime.of(2023, 3, 30, 14, 16, 30)

    val creatorUser1 = AgentUser("foo1", "Foo One")
    val modifierUser1 = AgentUser("bar1", "Bar One")
    val teamMember1 = AgentUser("baz1", "Baz One")
    val creatorUser2 = AgentUser("foo2", "Foo Two")
    val modifierUser2 = AgentUser("bar2", "Bar Two")
    val teamMember2 = AgentUser("baz2", "Baz Two")

    val client1 = Client("HMRC-MTD-VAT~VRN~123456789", "Johnny Vat")
    val client2 = Client("HMRC-PPT-ORG~EtmpRegistrationNUmber~XAPPT0000012345", "Johnny Plastic")

    val legacyCrypto = SymmetricCryptoFactory.aesGcmCrypto(someOldKey)
    val aesCrypto = SymmetricCryptoFactory.aesCrypto(someNewKey)

    val oldCustomRepo = new LegacyCustomGroupsRepository(mongoComponent)(implicitly[ExecutionContext], legacyCrypto)
    val oldTaxRepo = new LegacyTaxServiceGroupsRepository(mongoComponent)(implicitly[ExecutionContext], legacyCrypto)
    val oldOptInRepo = new LegacyOptinRepositoryImpl(mongoComponent, legacyCrypto)

    val newCustomRepo = new CustomGroupsRepositoryV2Impl(mongoComponent, aesCrypto)
    val newTaxRepo = new TaxGroupsRepositoryV2Impl(mongoComponent, aesCrypto)
    val newOptInRepo = new OptinRepositoryImpl(mongoComponent, aesCrypto)

    val appConfigStub = stub[AppConfig]
    val syncRepo = new EacdSyncRepositoryImpl(mongoComponent, appConfigStub)

    val migrateFunctionality =
      new MigrateToV2(
        syncRepo,
        oldCustomRepo,
        oldTaxRepo,
        newCustomRepo,
        newTaxRepo,
        oldOptInRepo,
        newOptInRepo,
        stub[Configuration]
      )

    val oldCustomGroup = LegacyCustomGroup(
      _id = new ObjectId().toString,
      arn = arn1,
      groupName = "The custom group",
      created = created1,
      createdBy = creatorUser1,
      lastUpdated = lastUpdated1,
      lastUpdatedBy = modifierUser1,
      teamMembers = Some(Set(teamMember1)),
      clients = Some(Set(client1))
    )

    val oldTaxGroup = LegacyTaxGroup(
      _id = new ObjectId().toString,
      arn = arn2,
      groupName = "The tax group",
      created = created2,
      createdBy = creatorUser2,
      lastUpdated = lastUpdated2,
      lastUpdatedBy = modifierUser2,
      teamMembers = Some(Set(teamMember2)),
      excludedClients = Some(Set(client2)),
      automaticUpdates = true,
      service = "HMRC-PPT-ORG"
    )

    "correctly migrate all data" in {
      (appConfigStub.eacdSyncNotBeforeSeconds _).when().returns(300)

      val optinRecord = OptinRecord(
        Arn("AARN0123456"),
        List(OptinEvent(OptedIn, AgentUser("userid", "name"), LocalDateTime.now()))
      )

      oldCustomRepo.collection.insertOne(LegacySensitiveAccessGroup(oldCustomGroup)).toFuture.futureValue
      oldTaxRepo.collection.insertOne(LegacySensitiveTaxServiceGroup(oldTaxGroup)).toFuture.futureValue
      oldOptInRepo.collection.insertOne(SensitiveOptinRecord(optinRecord)).toFuture.futureValue

      migrateFunctionality.doTheMigration().futureValue

      val customGroupsAfterMigration = newCustomRepo.collection.find().toFuture.futureValue
      val taxGroupsAfterMigration = newTaxRepo.collection.find().toFuture.futureValue
      val optInRecordsAfterMigration = newOptInRepo.collection
        .find(Filters.regex("arn", "^.ARN" /* filter out the 'backup' ones */ ))
        .map(_.decryptedValue)
        .toFuture
        .futureValue
      oldOptInRepo.collection
        .find(Filters.regex("arn", "^.ARN" /* filter out the 'backup' ones */ ))
        .toFuture
        .map(_.map(_.decryptedValue))
        .failed
        .futureValue shouldBe an[Exception] // if the old encryption algorithm tries to read it back, it should fail!

      customGroupsAfterMigration.length shouldBe 1
      taxGroupsAfterMigration.length shouldBe 1
      optInRecordsAfterMigration shouldBe Seq(
        optinRecord
      ) // we should find the same opt-in record - but re-encrypted with the new algorithm

      val newCustomGroup = customGroupsAfterMigration.head.decryptedValue
      val newTaxGroup = taxGroupsAfterMigration.head.decryptedValue

      newCustomGroup.arn shouldBe oldCustomGroup.arn
      newCustomGroup.groupName shouldBe oldCustomGroup.groupName
      newCustomGroup.created shouldBe oldCustomGroup.created
      newCustomGroup.createdBy shouldBe oldCustomGroup.createdBy
      newCustomGroup.lastUpdated shouldBe oldCustomGroup.lastUpdated
      newCustomGroup.lastUpdatedBy shouldBe oldCustomGroup.lastUpdatedBy
      newCustomGroup.teamMembers shouldBe oldCustomGroup.teamMembers.getOrElse(Set.empty)
      newCustomGroup.clients shouldBe oldCustomGroup.clients.getOrElse(Set.empty)

      newTaxGroup.arn shouldBe oldTaxGroup.arn
      newTaxGroup.groupName shouldBe oldTaxGroup.groupName
      newTaxGroup.created shouldBe oldTaxGroup.created
      newTaxGroup.createdBy shouldBe oldTaxGroup.createdBy
      newTaxGroup.lastUpdated shouldBe oldTaxGroup.lastUpdated
      newTaxGroup.lastUpdatedBy shouldBe oldTaxGroup.lastUpdatedBy
      newTaxGroup.teamMembers shouldBe oldTaxGroup.teamMembers.getOrElse(Set.empty)
      newTaxGroup.excludedClients shouldBe oldTaxGroup.excludedClients.getOrElse(Set.empty)
      newTaxGroup.service shouldBe oldTaxGroup.service
      newTaxGroup.automaticUpdates shouldBe oldTaxGroup.automaticUpdates
    }

    "fail if there is already any data in the new repositories" in {
      newCustomRepo.collection
        .insertOne(
          SensitiveCustomGroup(
            CustomGroup(
              id = GroupId.random(),
              arn = arn1,
              groupName = "The Group",
              created = created1,
              lastUpdated = created1,
              createdBy = creatorUser1,
              lastUpdatedBy = modifierUser1,
              teamMembers = Set.empty,
              clients = Set.empty
            )
          )
        )
        .toFuture
        .futureValue

      migrateFunctionality.doTheMigration().failed.futureValue should matchPattern {
        case MigrationAbortedException(_, false) => // no cleanup
      }
    }

    "fail if lock cannot be acquired" in {
      (appConfigStub.eacdSyncNotBeforeSeconds _).when().returns(300)

      syncRepo
        .acquire(Arn("MIGRATELOCK"))
        .futureValue // Already acquire the lock so the migration cannot re-acquire it later
      migrateFunctionality.doTheMigration().failed.futureValue should matchPattern {
        case MigrationAbortedException(_, false) => // no cleanup
      }
    }

    "clean up data correctly in case of failure" in {
      (appConfigStub.eacdSyncNotBeforeSeconds _).when().returns(300)

      val optinTime = LocalDateTime.now()

      // set up
      (for {
        _ <- oldCustomRepo.collection.insertOne(LegacySensitiveAccessGroup(oldCustomGroup)).toFuture
        _ <- oldTaxRepo.collection.insertOne(LegacySensitiveTaxServiceGroup(oldTaxGroup)).toFuture
        _ <- newCustomRepo.collection
               .insertOne(SensitiveCustomGroup(migrateFunctionality.migrateCustomGroup(oldCustomGroup)))
               .toFuture
        _ <-
          newTaxRepo.collection.insertOne(SensitiveTaxGroup(migrateFunctionality.migrateTaxGroup(oldTaxGroup))).toFuture
        _ <- oldOptInRepo.collection
               .insertOne(
                 SensitiveOptinRecord(
                   OptinRecord(
                     Arn("BACKUPAARN0123456"),
                     List(OptinEvent(OptedIn, AgentUser("userid", "name"), optinTime))
                   )
                 )
               )
               .toFuture
        _ <-
          newOptInRepo.collection.insertOne(SensitiveOptinRecord(OptinRecord(Arn("BARN0987654"), List.empty))).toFuture
      } yield ()).futureValue

      migrateFunctionality.cleanup().futureValue

      // verification
      oldCustomRepo.collection.countDocuments.toFuture.futureValue shouldBe 1 // old repo should be intact
      newCustomRepo.collection.countDocuments.toFuture.futureValue shouldBe 0 // new repo should be deleted
      oldTaxRepo.collection.countDocuments.toFuture.futureValue shouldBe 1 // old repo should be intact
      newTaxRepo.collection.countDocuments.toFuture.futureValue shouldBe 0 // new repo should be deleted
      // opt in record should have the old data with "backup" tags removed. Any new data should be deleted.
//  Note: opt-in cleanup currently disabled
//      oldOptInRepo.collection.find(Filters.empty).map(_.decryptedValue).toFuture.futureValue shouldBe Seq(
//        OptinRecord(Arn("AARN0123456"), List(OptinEvent(OptedIn, AgentUser("userid", "name"), optinTime)))
//      )
    }
  }
}
