/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.util

import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.model.ReplaceOneModel
import org.mongodb.scala.model.Sorts.ascending
import play.api.Logging
import uk.gov.hmrc.mongo.play.json.Codecs.DocumentOps
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}

import scala.concurrent.duration.{Deadline, Duration}
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

trait Migrations:
  def migrate(batchSize: Int, deadline: Deadline): Future[Int]

trait PlayMongoMigrations(using ExecutionContext) extends Migrations with Transactions:
  self: PlayMongoRepository[?] & Logging =>

  /** Runs a server-side migration in batches until all records are processed or the deadline is reached.
    *
    * The migration mechanism is the repository's current domain format: each stored record is read,
    * decoded with the local model format, then written back. This allows schema evolution to be
    * applied by the latest application codecs without a separate one-off mapping layer.
    *
    * Each batch runs in a transaction because records may be updated concurrently while migration is
    * in progress. Transactional execution ensures each read/replace step is applied safely.
    *
    * If the deadline is exceeded, the migration fails with a timeout.
    *
    * @param batchSize
    *   the maximum number of records to process per transaction
    * @param deadline
    *   the overall time budget for the migration
    * @return
    *   a future containing the total number of modified documents
    */
  def migrate(batchSize: Int, deadline: Deadline): Future[Int] =
    given TransactionConfiguration = TransactionConfiguration.strict

    def asUpdate(document: Document) =
      val domainModel = document.fromBson(using self.domainFormat)
      val isUnchanged = and(equal("_id", document("_id")))

      ReplaceOneModel(filter = isUnchanged, replacement = domainModel)

    def replaceBatch(cursor: Bson) =
      withSessionAndTransaction: session =>
        logger.info(s"transaction=${session.getServerSession.getTransactionNumber}}")
        for
          documents <- collection
                         .find[Document](session, cursor)
                         .sort(ascending("_id"))
                         .limit(batchSize)
                         .maxTime(deadline.timeLeft.max(Duration.Zero))
                         .toFuture()

          resultsOrNone <-
            if documents.nonEmpty then collection.bulkWrite(session, documents.map(asUpdate)).headOption()
            else Future.successful(None)

        yield resultsOrNone.map: results =>
          documents.last("_id") -> results.getModifiedCount

    // Repeatedly execute the batch replacement, advancing the cursor to the last processed `_id`
    // until there are no more matching documents to migrate.
    def loop(cursor: Bson, totalModified: Int): Future[Int] =
      if deadline.isOverdue then Future.failed(new TimeoutException("Key rotation migration exceeded maximum duration"))
      else
        replaceBatch(cursor)
          .flatMap:
            case Some(lastId -> modified) => loop(cursor = gt("_id", lastId), totalModified + modified)
            case None                     => Future.successful(totalModified)

    loop(cursor = Document.empty, totalModified = 0)
