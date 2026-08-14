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
import com.mongodb.client.model.{Collation, IndexOptions}
import org.mongodb.scala.model.*
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import play.api.Logging
import play.api.libs.json.Format
import uk.gov.hmrc.agentpermissions.model.accessgroups.CustomGroup
import uk.gov.hmrc.agentpermissions.model.{Arn, SensitiveCustomGroup}
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2Impl.*
import uk.gov.hmrc.agentpermissions.util.{Migrations, PlayMongoMigrations}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CustomGroupsRepositoryV2Impl])
trait CustomGroupsRepositoryV2 extends Migrations {

  /* TODO: add following functionality
   *   [ ] pull list of tm from group
   *   [ ] pull list of clients from group
   *   [ ] find client in group by ids
   *   [ ] find team member in group by ids (decrypted)
   *  to be used for find Client & Team member pair in group by ids
   *
   * */

  def findById(id: GroupId): Future[Option[CustomGroup]]
  def get(arn: Arn): Future[Seq[CustomGroup]]
  def get(arn: Arn, groupName: String): Future[Option[CustomGroup]]
  def insert(accessGroup: CustomGroup): Future[Option[String]]
  def delete(arn: Arn, groupName: String): Future[Option[Long]]
  def update(arn: Arn, groupName: String, accessGroup: CustomGroup): Future[Option[Long]]
  def delete(arn: String): Future[Long]
}

@Singleton
class CustomGroupsRepositoryV2Impl @Inject() (
  val mongoComponent: MongoComponent,
  @Named("aes") crypto: Encrypter & Decrypter
)(using ec: ExecutionContext)
    extends PlayMongoRepository[SensitiveCustomGroup](
      collectionName = "access-groups-custom",
      domainFormat = SensitiveCustomGroup.databaseFormat(using crypto),
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
      ),
      extraCodecs = Seq(
        // Sensitive string codec so we can operate on individual string fields
        Codecs.playFormatCodec(sensitiveStringFormat(using crypto))
      )
    ) with CustomGroupsRepositoryV2 with PlayMongoMigrations with Logging {

  given theCrypto: Encrypter & Decrypter = crypto

  def findById(id: GroupId): Future[Option[CustomGroup]] =
    collection
      .find(Filters.equal("_id", id.toString))
      .map(_.decryptedValue)
      .headOption()

  def get(arn: Arn): Future[Seq[CustomGroup]] =
    collection
      .find(equal(FIELD_ARN, arn.value))
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .collect()
      .toFuture()

  def get(arn: Arn, groupName: String): Future[Option[CustomGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)))
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .headOption()

  def insert(customGroup: CustomGroup): Future[Option[String]] =
    collection
      .insertOne(SensitiveCustomGroup(customGroup))
      .headOption()
      .map(_.map(_.getInsertedId.asString().getValue))
      .recoverWith { case _: MongoWriteException =>
        Future.successful(None)
      }

  def delete(arn: Arn, groupName: String): Future[Option[Long]] =
    collection
      .deleteOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        deleteOptions
      )
      .headOption()
      .map(_.map(_.getDeletedCount))

  def update(arn: Arn, groupName: String, customGroup: CustomGroup): Future[Option[Long]] =
    collection
      .replaceOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        SensitiveCustomGroup(customGroup),
        replaceOptions
      )
      .headOption()
      .map(_.map(_.getModifiedCount))

  private lazy val deleteOptions: DeleteOptions = new DeleteOptions().collation(caseInsensitiveCollation)

  private lazy val replaceOptions: ReplaceOptions =
    new ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)

  // test only
  override def delete(arn: String): Future[Long] =
    collection.deleteMany(equal("arn", arn)).toFuture().map(_.getDeletedCount)
}

object CustomGroupsRepositoryV2Impl {
  private val FIELD_ARN = "arn"
  private val FIELD_GROUPNAME = "groupName"

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()

  private def sensitiveStringFormat(using crypto: Encrypter & Decrypter): Format[SensitiveString] =
    JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
}
