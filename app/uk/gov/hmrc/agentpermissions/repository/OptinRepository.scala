/*
 * Copyright 2022 HM Revenue & Customs
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
import com.mongodb.client.model.{IndexOptions, ReplaceOptions}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.Indexes.ascending
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptinRecord}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait UpsertType
case object RecordInserted extends UpsertType
case object RecordUpdated extends UpsertType

@ImplementedBy(classOf[OptinRepositoryImpl])
trait OptinRepository {
  def get(arn: Arn): Future[Option[OptinRecord]]
  def upsert(optinRecord: OptinRecord): Future[Option[UpsertType]]
}

@Singleton
class OptinRepositoryImpl @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[OptinRecord](
      collectionName = "optin",
      domainFormat = OptinRecord.formatOptinRecord,
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(ascending("arn"), new IndexOptions().name("arnIdx").unique(true))
      )
    ) with OptinRepository with Logging {

  def get(arn: Arn): Future[Option[OptinRecord]] = collection.find(equal("arn", arn.value)).headOption()

  def upsert(optinRecord: OptinRecord): Future[Option[UpsertType]] =
    collection
      .replaceOne(equal("arn", optinRecord.arn.value), optinRecord, upsertOptions)
      .headOption()
      .map(_.map(_.getModifiedCount match {
        case 0L => RecordInserted
        case 1L => RecordUpdated
        case x  => throw new RuntimeException(s"Update modified count should not have been $x")
      }))

  private def upsertOptions = new ReplaceOptions().upsert(true)
}
