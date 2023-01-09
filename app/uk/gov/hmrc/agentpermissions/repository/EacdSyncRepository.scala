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
import org.mongodb.scala.model.Filters.{and, equal, lt}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions}
import play.api.Logging
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EacdSyncRepositoryImpl])
trait EacdSyncRepository {
  def acquire(arn: Arn, notBeforeSeconds: Int): Future[Option[EacdSyncRecord]]
}

@Singleton
class EacdSyncRepositoryImpl @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends {
      private val ARN = "arn"
      private val UPDATED_AT = "updatedAt"
    } with PlayMongoRepository[EacdSyncRecord](
      collectionName = "eacd-sync-records",
      domainFormat = EacdSyncRecord.format,
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(ascending(ARN), new IndexOptions().name("arnIdx").unique(true))
      )
    ) with EacdSyncRepository with Logging {

  private val replaceOptions = ReplaceOptions().upsert(true)

  override def acquire(arn: Arn, notBeforeSeconds: Int): Future[Option[EacdSyncRecord]] = {
    lazy val beforeInstant = Instant.now().minusSeconds(notBeforeSeconds)
    val searchFilters = and(equal(ARN, arn.value), lt(UPDATED_AT, beforeInstant))

    val eacdSyncRecord = EacdSyncRecord(arn, Instant.now())

    collection
      .replaceOne(searchFilters, eacdSyncRecord, replaceOptions)
      .headOption()
      .map(_.flatMap(_ => Some(eacdSyncRecord)))
      .recoverWith {
        case ex: MongoWriteException if ex.getError.getCode == 11000 =>
          logger.debug(s"Cannot acquire as being tried within refresh interval")
          Future successful Option.empty[EacdSyncRecord]
      }
  }
}

case class EacdSyncRecord(arn: Arn, updatedAt: Instant)

object EacdSyncRecord {
  implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val format: Format[EacdSyncRecord] = Json.format[EacdSyncRecord]
}
