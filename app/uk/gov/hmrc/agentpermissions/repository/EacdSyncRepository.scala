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

import com.google.inject.ImplementedBy
import com.mongodb.MongoWriteException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, ReplaceOptions}
import play.api.Logging
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.SECONDS
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EacdSyncRepositoryImpl])
trait EacdSyncRepository {
  def acquire(arn: Arn): Future[Option[EacdSyncRecord]]
}

@Singleton
class EacdSyncRepositoryImpl @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)(implicit
  ec: ExecutionContext
) extends {
      private val ARN = "arn"
      private val UPDATED_AT = "updatedAt"
    } with PlayMongoRepository[EacdSyncRecord](
      collectionName = "eacd-sync-records",
      domainFormat = EacdSyncRecord.format,
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(ascending(ARN), new IndexOptions().name("arnIdx").unique(true)),
        IndexModel(
          ascending(UPDATED_AT),
          IndexOptions()
            .background(false)
            .name("idxUpdatedAt")
            .expireAfter(appConfig.eacdSyncNotBeforeSeconds, SECONDS)
        )
      )
    ) with EacdSyncRepository with Logging {

  override def acquire(arn: Arn): Future[Option[EacdSyncRecord]] = {
    val newRecord = EacdSyncRecord(arn, Instant.now())
    for {
      // We are doing some overkill sanity deleting of old records as we have had some problems before
      _ <- collection
             .deleteMany(
               Filters.and(
                 Filters.equal(ARN, arn.value),
                 Filters.lt(UPDATED_AT, Instant.now().minusSeconds(appConfig.eacdSyncNotBeforeSeconds))
               )
             )
             .toFuture
      syncRecords <- collection.find(equal(ARN, arn.value)).toFuture
      maybeNewRecord <- syncRecords.toList match {
                          case Nil =>
                            collection
                              .replaceOne(equal(ARN, arn.value), newRecord, ReplaceOptions().upsert(true))
                              .headOption()
                              .map(_.map(_ => newRecord))
                          case _ =>
                            logger.debug(s"Cannot acquire as an un-expired record already exists in the collection")
                            Future.successful(None) // There are existing records in db; cannot acquire
                        }
    } yield maybeNewRecord
  }
}

case class EacdSyncRecord(arn: Arn, updatedAt: Instant)

object EacdSyncRecord {
  implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: Format[EacdSyncRecord] = Json.format[EacdSyncRecord]
}
