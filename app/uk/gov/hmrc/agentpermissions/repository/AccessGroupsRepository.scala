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
import com.mongodb.client.model.{Collation, IndexOptions}
import com.mongodb.{BasicDBObject, MongoWriteException}
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model.{DeleteOptions, IndexModel, ReplaceOptions}
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn}
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepositoryImpl.{FIELD_ARN, FIELD_GROUPNAME, caseInsensitiveCollation}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsRepositoryImpl])
trait AccessGroupsRepository {
  def findById(id: String): Future[Option[AccessGroup]]
  def get(arn: Arn): Future[Seq[AccessGroup]]
  def get(arn: Arn, groupName: String): Future[Option[AccessGroup]]
  def insert(accessGroup: AccessGroup): Future[Option[String]]
  def delete(arn: Arn, groupName: String): Future[Option[Long]]
  def update(arn: Arn, groupName: String, accessGroup: AccessGroup): Future[Option[Long]]
}

@Singleton
class AccessGroupsRepositoryImpl @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[AccessGroup](
      collectionName = "access-groups",
      domainFormat = AccessGroup.formatAccessGroup,
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(ascending(FIELD_ARN), new IndexOptions().name("arnIdx").unique(false)),
        IndexModel(
          compoundIndex(ascending(FIELD_ARN), ascending(FIELD_GROUPNAME)),
          new IndexOptions()
            .name("arnGroupNameIdx")
            .unique(true)
            .collation(caseInsensitiveCollation)
        )
      )
    ) with AccessGroupsRepository with Logging {

  override def findById(id: String): Future[Option[AccessGroup]] =
    collection
      .find(new BasicDBObject("_id", id))
      .headOption()

  override def get(arn: Arn): Future[Seq[AccessGroup]] =
    collection
      .find(equal(FIELD_ARN, arn.value))
      .collation(caseInsensitiveCollation)
      .collect()
      .toFuture()

  override def get(arn: Arn, groupName: String): Future[Option[AccessGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)))
      .collation(caseInsensitiveCollation)
      .headOption()

  override def insert(accessGroup: AccessGroup): Future[Option[String]] =
    collection
      .insertOne(accessGroup)
      .headOption()
      .map(_.map(result => result.getInsertedId.asString().getValue))
      .recoverWith { case e: MongoWriteException =>
        Future.successful(None)
      }

  override def delete(arn: Arn, groupName: String): Future[Option[Long]] =
    collection
      .deleteOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        deleteOptions
      )
      .headOption()
      .map(_.map(result => result.getDeletedCount))

  override def update(arn: Arn, groupName: String, accessGroup: AccessGroup): Future[Option[Long]] =
    collection
      .replaceOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        accessGroup,
        replaceOptions
      )
      .headOption()
      .map(_.map(result => result.getModifiedCount))

  private lazy val deleteOptions: DeleteOptions = new DeleteOptions().collation(caseInsensitiveCollation)

  private lazy val replaceOptions: ReplaceOptions =
    new ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)

}

object AccessGroupsRepositoryImpl {
  private val FIELD_ARN = "arn"
  private val FIELD_GROUPNAME = "groupName"

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()
}
