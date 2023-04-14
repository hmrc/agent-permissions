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

import org.mongodb.scala.model.Filters
import play.api.{Configuration, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.model.SensitiveOptinRecord
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2Impl, LegacyOptinRepositoryImpl, OptinRepositoryImpl, TaxGroupsRepositoryV2Impl}
import uk.gov.hmrc.agents.accessgroups.{CustomGroup, TaxGroup}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class MigrateToV2 @Inject() (
  oldCustomGroupsRepo: LegacyCustomGroupsRepository,
  oldTaxGroupsRepo: LegacyTaxServiceGroupsRepository,
  newCustomGroupsRepo: CustomGroupsRepositoryV2Impl,
  newTaxGroupsRepo: TaxGroupsRepositoryV2Impl,
  oldOptInRepo: LegacyOptinRepositoryImpl,
  newOptInRepo: OptinRepositoryImpl,
  config: Configuration
)(implicit ec: ExecutionContext)
    extends Logging {

  /* DO IT */
  Try(config.get[String]("migrateData")) match {
    case Success("v1-to-v2") => doTheMigration()
    case Success(x)          => logger.error(s"Unknown config value: migrateData = $x. No migration performed.")
    case Failure(_)          => logger.info(s"Migration config key not found. No migration performed.")
  }

  private[legacy] def migrateCustomGroup(legacyCustomGroup: LegacyCustomGroup): CustomGroup = CustomGroup(
    id = GroupId.random(),
    arn = legacyCustomGroup.arn,
    groupName = legacyCustomGroup.groupName,
    created = legacyCustomGroup.created,
    lastUpdated = legacyCustomGroup.lastUpdated,
    createdBy = legacyCustomGroup.createdBy,
    lastUpdatedBy = legacyCustomGroup.lastUpdatedBy,
    teamMembers = legacyCustomGroup.teamMembers.getOrElse(Set.empty),
    clients = legacyCustomGroup.clients.getOrElse(Set.empty)
  )

  private[legacy] def migrateTaxGroup(legacyTaxGroup: LegacyTaxGroup): TaxGroup = TaxGroup(
    id = GroupId.random(),
    arn = legacyTaxGroup.arn,
    groupName = legacyTaxGroup.groupName,
    created = legacyTaxGroup.created,
    lastUpdated = legacyTaxGroup.lastUpdated,
    createdBy = legacyTaxGroup.createdBy,
    lastUpdatedBy = legacyTaxGroup.lastUpdatedBy,
    teamMembers = legacyTaxGroup.teamMembers.getOrElse(Set.empty),
    service = legacyTaxGroup.service,
    automaticUpdates = legacyTaxGroup.automaticUpdates,
    excludedClients = legacyTaxGroup.excludedClients.getOrElse(Set.empty)
  )

  def doTheMigration(): Future[Unit] =
    (for {
      nrNewCustomGroups <- newCustomGroupsRepo.collection.countDocuments().toFuture
      nrNewTaxGroups    <- newTaxGroupsRepo.collection.countDocuments().toFuture
      _ = logger.warn("========== Access groups migration started ==========")
      _ =
        logger.warn(s"There are $nrNewCustomGroups custom groups and $nrNewTaxGroups tax groups in the V2 repository.")
      _ = if (nrNewCustomGroups == 0L && nrNewTaxGroups == 0L)
            logger.warn("Nothing in V2 repositories yet. Migration can proceed.")
          else {
            logger.warn("There is existing data in V2 repositories! Migration aborted.")
            throw new IllegalStateException("V2 migration aborted due to existing data in V2 repositories.")
          }
      nrOldCustomGroups <- oldCustomGroupsRepo.collection.countDocuments().toFuture
      nrOldTaxGroups    <- oldTaxGroupsRepo.collection.countDocuments().toFuture
      _ = logger.warn(
            s"There are $nrOldCustomGroups custom groups and $nrOldTaxGroups tax groups in the LEGACY repository."
          )

      // do the custom groups
      _ = logger.warn("Copying across the custom groups...")
      allOldCustomGroups <- oldCustomGroupsRepo.collection.find().toFuture
      allNewCustomGroups = allOldCustomGroups.map(scg => migrateCustomGroup(scg.decryptedValue))
      _ <- Future.traverse(allNewCustomGroups)(newCustomGroupsRepo.insert)
      // do the tax groups
      _ = logger.warn("Copying across the tax groups...")
      allOldTaxGroups <- oldTaxGroupsRepo.collection.find().toFuture
      allNewTaxGroups = allOldTaxGroups.map(stg => migrateTaxGroup(stg.decryptedValue))
      _ <- Future.traverse(allNewTaxGroups)(newTaxGroupsRepo.insert)
      _ = logger.warn("Copying finished.")

      _ = logger.warn("Reading back new groups...")
      readbackNewCustomGroups <- newCustomGroupsRepo.collection.find().toFuture.map(_.map(_.decryptedValue))
      readbackNewTaxGroups    <- newTaxGroupsRepo.collection.find().toFuture.map(_.map(_.decryptedValue))
      _ =
        logger.warn(
          s"After migration there are ${readbackNewCustomGroups.length} custom groups and ${readbackNewTaxGroups.length} tax groups in the V2 repository."
        )
      _ = logger.warn("Verifying data integrity...")
      _ = require(readbackNewCustomGroups.length == allOldCustomGroups.length)
      _ = require(readbackNewTaxGroups.length == allOldTaxGroups.length)
      _ = require(allNewCustomGroups.sortBy(_.arn.value) == readbackNewCustomGroups.sortBy(_.arn.value))
      _ = require(allNewTaxGroups.sortBy(_.arn.value) == readbackNewTaxGroups.sortBy(_.arn.value))
      _ = logger.warn("Data integrity passed.")

      _ = logger.warn("Migrating opt-in repository...")
      allOptInRecords <- oldOptInRepo.collection.find().toFuture.map(_.map(_.decryptedValue))
      _ = logger.warn("Backing up legacy records...")
      backupOptInRecords = allOptInRecords.map(oir => oir.copy(arn = Arn("BACKUP" + oir.arn.value)))
      _ <- if (backupOptInRecords.nonEmpty)
             oldOptInRepo.collection.insertMany(backupOptInRecords.map(SensitiveOptinRecord(_))).toFuture
           else Future.successful(())
      _ = logger.warn("Inserting updated records...")
      _ <- Future.traverse(allOptInRecords)(newOptInRepo.upsert)
      _ = logger.warn("Verifying opt-in integrity...")
      backedUpRecordCount <- newOptInRepo.collection.countDocuments(Filters.regex("arn", "^BACKUP")).toFuture
      normalRecordCount   <- newOptInRepo.collection.countDocuments(Filters.regex("arn", "^.ARN")).toFuture
      _ = require(backedUpRecordCount == normalRecordCount)
      _ = logger.warn("Opt-in migration done.")

      _ = logger.warn("[IMPORTANT] Now disable the migration task in config and restart the service.")
      _ = logger.warn("========== Access groups migration finished ==========")
    } yield ()).recoverWith { case e =>
      logger.error("The migration failed! Deleting V2 repositories.")
      Future
        .sequence(
          Seq(
            newCustomGroupsRepo.collection.drop().toFuture,
            newTaxGroupsRepo.collection.drop().toFuture
          )
        )
        .map(_ => throw e)
    }
}
