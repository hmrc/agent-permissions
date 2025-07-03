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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Filters.{and, equal, exists}
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model._
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import play.api.libs.json.Format
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.CustomGroupsRepositoryV2Impl._
import uk.gov.hmrc.agentpermissions.repository.storagemodel.{SensitiveAgentUser, SensitiveCustomGroup}
import uk.gov.hmrc.agents.accessgroups.{AgentUser, CustomGroup}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CustomGroupsRepositoryV2Impl])
trait CustomGroupsRepositoryV2 {

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

  /* TODO is it possible to update lastUpdated/lastUpdatedBy fields at the same time for addTeamMember and removeClient?
   *   if only accomplished by update, remove these methods
   * */
  def addTeamMember(id: GroupId, toAdd: AgentUser): Future[UpdateResult]
  def removeClient(groupId: GroupId, clientId: String): Future[UpdateResult]

  def delete(arn: String): Future[Long]
}

@Singleton
class CustomGroupsRepositoryV2Impl @Inject() (
  mongoComponent: MongoComponent,
  @Named("aes") crypto: Encrypter with Decrypter
)(implicit ec: ExecutionContext, mat: Materializer)
    extends PlayMongoRepository[SensitiveCustomGroup](
      collectionName = "access-groups-custom",
      domainFormat = SensitiveCustomGroup.databaseFormat(crypto),
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
        Codecs.playFormatCodec(sensitiveStringFormat(crypto))
      )
    ) with CustomGroupsRepositoryV2 with Logging {

  // Ensure that we are using a deterministic cryptographic algorithm, or we won't be able to search on encrypted fields
  require(
    crypto.encrypt(PlainText("foo")) == crypto.encrypt(PlainText("foo")),
    s"Crypto algorithm provided is not deterministic."
  )

  implicit val theCrypto: Encrypter with Decrypter = crypto

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
      .map(_.map(result => result.getInsertedId.asString().getValue))
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
      .map(_.map(result => result.getDeletedCount))

  def update(arn: Arn, groupName: String, customGroup: CustomGroup): Future[Option[Long]] =
    collection
      .replaceOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        SensitiveCustomGroup(customGroup),
        replaceOptions
      )
      .headOption()
      .map(_.map(result => result.getModifiedCount))

  private lazy val deleteOptions: DeleteOptions = new DeleteOptions().collation(caseInsensitiveCollation)

  private lazy val replaceOptions: ReplaceOptions =
    new ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)

  def addTeamMember(id: GroupId, agentUser: AgentUser): Future[UpdateResult] =
    collection
      .updateOne(
        filter = Filters.equal("_id", id.toString),
        update = Updates.addToSet("teamMembers", Codecs.toBson(SensitiveAgentUser(agentUser)))
      )
      .head()

  def removeClient(groupId: GroupId, enrolmentKey: String): Future[UpdateResult] =
    collection
      .updateOne(
        filter = Filters.equal("_id", groupId.toString),
        update = Updates.pullByFilter(
          Document(
            "clients" -> Document("enrolmentKey" -> Codecs.toBson(SensitiveString(enrolmentKey))(sensitiveStringFormat))
          )
        )
      )
      .head()

  // test only
  override def delete(arn: String): Future[Long] =
    collection.deleteMany(equal("arn", arn)).toFuture().map(_.getDeletedCount)

  def countUnencrypted(): Future[Long] = collection.countDocuments(exists("encrypted", exists = false)).toFuture()

  def encryptOldRecords(rate: Int = 10): Unit = {
    val observable = collection.find(exists("encrypted", exists = false))
    countUnencrypted().map { count =>
      logger.warn(s"[CustomGroupsRepositoryV2] automatic encryption has started, $count applications left to encrypt")
    }
    Source
      .fromPublisher(observable)
      .throttle(rate, 1.second)
      .runForeach { record =>
        collection
          .replaceOne(equal("_id", record._id), record)
          .toFuture()
          .map { _ =>
            logger.warn("[CustomGroupsRepositoryV2] successfully encrypted record")
          }
          .recover { case ex: Throwable =>
            logger.warn("[CustomGroupsRepositoryV2] failed to encrypt record", ex)
          }
        ()
      }
      .onComplete { _ =>
        countUnencrypted().map { count =>
          logger.warn(s"[CustomGroupsRepositoryV2] encryption completed, $count applications left unencrypted")
        }
      }
  }

  encryptOldRecords()
}

object CustomGroupsRepositoryV2Impl {
  private val FIELD_ARN = "arn"
  private val FIELD_GROUPNAME = "groupName"

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()

  private def sensitiveStringFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveString] =
    JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
}
