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
import play.api.libs.json.JsObject
import play.api.{Configuration, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.repository.{EacdSyncRepository, OptinRepositoryImpl}
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class MigrationAbortedException(message: String, needsCleanup: Boolean) extends RuntimeException

@Singleton
class MigrateTool @Inject() (
  mongo: MongoComponent,
  syncRepo: EacdSyncRepository,
  optInRepo: OptinRepositoryImpl,
  config: Configuration
)(implicit ec: ExecutionContext)
    extends Logging {

  /* DO IT */
  Try(config.get[String]("migrateData")) match {
    case Success("delete-v1-legacy-data") => doTheMigration()
    case Success(x) => logger.error(s"Unknown config value: migrateData = $x. No migration performed.")
    case Failure(_) => logger.info(s"Migration config key not found. No migration performed.")
  }

  def doTheMigration(): Future[Unit] =
    for {
      maybeLock <- syncRepo.acquire(Arn("MIGRATELOCK")) // abusing the EACD sync repo to acquire a migration lock
      _ = if (maybeLock.isEmpty) {
            logger.error("Could not acquire migration lock. Aborting.")
            throw MigrationAbortedException("Could not acquire migration lock", needsCleanup = false)
          }
      oldCustomGroupsCollection = mongo.database.getCollection[JsObject]("access-groups")
      oldTaxGroupsCollection = mongo.database.getCollection[JsObject]("tax-service-groups")
      _ = logger.warn("========== Cleaning up legacy data ==========")
      _ <- oldCustomGroupsCollection.drop().toFuture
      _ = logger.warn("Dropped old custom groups collection.")
      _ <- oldTaxGroupsCollection.drop().toFuture
      _ = logger.warn("Dropped old tax groups collection.")
      nrBackupOirs <- optInRepo.collection.countDocuments(Filters.regex("arn", "^BACKUP")).toFuture
      nrActualOirs <- optInRepo.collection.countDocuments(Filters.regex("arn", "^.ARN")).toFuture
      _ = logger.warn(s"Opt-in records: $nrBackupOirs backups to delete, $nrActualOirs active.")
      _ = logger.warn("Deleting backup opt-in records...")
      _            <- optInRepo.collection.deleteMany(Filters.regex("arn", "^BACKUP")).toFuture
      nrBackupOirs <- optInRepo.collection.countDocuments(Filters.regex("arn", "^BACKUP")).toFuture
      nrActualOirs <- optInRepo.collection.countDocuments(Filters.regex("arn", "^.ARN")).toFuture
      _ = logger.warn(s"Opt-in records after deletion: $nrBackupOirs backups to delete, $nrActualOirs active.")
      _ = logger.warn("========== Legacy data cleanup finished ==========")
    } yield ()
}
