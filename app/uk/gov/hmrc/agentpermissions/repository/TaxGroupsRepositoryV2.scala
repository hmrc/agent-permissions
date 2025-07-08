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
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Filters.{and, equal, exists}
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model._
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.storagemodel.{SensitiveAgentUser, SensitiveTaxGroup}
import uk.gov.hmrc.agents.accessgroups.{AgentUser, TaxGroup}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxGroupsRepositoryV2Impl])
trait TaxGroupsRepositoryV2 {
  def findById(id: GroupId): Future[Option[TaxGroup]]
  def get(arn: Arn): Future[Seq[TaxGroup]]
  def get(arn: Arn, groupName: String): Future[Option[TaxGroup]]
  def getByService(arn: Arn, service: String): Future[Option[TaxGroup]]
  def groupExistsForTaxService(arn: Arn, service: String): Future[Boolean]
  def insert(accessGroup: TaxGroup): Future[Option[String]]
  def delete(arn: Arn, groupName: String): Future[Option[Long]]
  def update(arn: Arn, groupName: String, accessGroup: TaxGroup): Future[Option[Long]]
  def addTeamMember(id: GroupId, toAdd: AgentUser): Future[UpdateResult]
}

import uk.gov.hmrc.agentpermissions.repository.TaxGroupsRepositoryV2Impl._

@Singleton
class TaxGroupsRepositoryV2Impl @Inject() (
  mongoComponent: MongoComponent,
  @Named("aes") crypto: Encrypter with Decrypter
)(implicit ec: ExecutionContext, mat: Materializer)
    extends PlayMongoRepository[SensitiveTaxGroup](
      collectionName = "access-groups-tax",
      domainFormat = SensitiveTaxGroup.databaseFormat(crypto),
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
    ) with TaxGroupsRepositoryV2 with Logging {

  // Ensure that we are using a deterministic cryptographic algorithm, or we won't be able to search on encrypted fields
  require(
    crypto.encrypt(PlainText("foo")) == crypto.encrypt(PlainText("foo")),
    s"Crypto algorithm provided is not deterministic."
  )

  implicit val theCrypto: Encrypter with Decrypter = crypto

  override def findById(id: GroupId): Future[Option[TaxGroup]] =
    collection
      .find(Filters.equal("_id", id.toString))
      .map(_.decryptedValue)
      .headOption()

  override def get(arn: Arn): Future[Seq[TaxGroup]] =
    collection
      .find(equal(FIELD_ARN, arn.value))
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .collect()
      .toFuture()

  // check usage
  override def get(arn: Arn, groupName: String): Future[Option[TaxGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)))
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .headOption()

  override def getByService(arn: Arn, service: String): Future[Option[TaxGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_SERVICE, service)))
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .headOption()

  def groupExistsForTaxService(arn: Arn, service: String): Future[Boolean] = {
    // Services as 1 entity with multiple enrolments stored as single group
    val svc = service match {
      case _ if service.contains("HMRC-TERS")   => "HMRC-TERS" // HMRC-TERS-ORG & HMRC-TERSNT-ORG
      case _ if service.contains("HMRC-CBC")    => "HMRC-CBC" // HMRC-CBC-ORG & HMRC-CBC-NONUK-ORG
      case _ if service.contains("HMRC-MTD-IT") => "HMRC-MTD-IT" // HMRC-MTD-IT & HMRC-MTD-IT-SUPP
      case _                                    => service
    }
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_SERVICE, svc)))
      .collation(caseInsensitiveCollation)
      .toFuture()
      .map(_.nonEmpty)
  }

  override def insert(accessGroup: TaxGroup): Future[Option[String]] =
    collection
      .insertOne(SensitiveTaxGroup(accessGroup))
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

  override def update(arn: Arn, groupName: String, accessGroup: TaxGroup): Future[Option[Long]] =
    collection
      .replaceOne(
        and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)),
        SensitiveTaxGroup(accessGroup),
        replaceOptions
      )
      .headOption()
      .map(_.map(result => result.getModifiedCount))

  private lazy val deleteOptions: DeleteOptions = new DeleteOptions().collation(caseInsensitiveCollation)

  private lazy val replaceOptions: ReplaceOptions =
    new ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)

  override def addTeamMember(id: GroupId, agentUser: AgentUser): Future[UpdateResult] =
    collection
      .updateOne(
        filter = Filters.equal("_id", id.toString),
        update = Updates.addToSet("teamMembers", Codecs.toBson(SensitiveAgentUser(agentUser)))
      )
      .head()

  def countUnencrypted(): Future[Long] = collection.countDocuments(exists("encrypted", exists = false)).toFuture()

  def encryptOldRecords(rate: Int = 10): Unit = {
    val observable = collection.find(exists("encrypted", exists = false))
    countUnencrypted().map { count =>
      logger.warn(s"[TaxGroupsRepositoryV2] automatic encryption has started, $count applications left to encrypt")
    }
    Source
      .fromPublisher(observable)
      .throttle(rate, 1.second)
      .runForeach { record =>
        collection
          .replaceOne(equal("_id", record._id), record)
          .toFuture()
          .map { _ =>
            logger.warn("[TaxGroupsRepositoryV2] successfully encrypted record")
          }
          .recover { case ex: Throwable =>
            logger.warn("[TaxGroupsRepositoryV2] failed to encrypt record", ex)
          }
        ()
      }
      .onComplete { _ =>
        countUnencrypted().map { count =>
          logger.warn(s"[TaxGroupsRepositoryV2] encryption completed, $count applications left unencrypted")
        }
      }
  }

  encryptOldRecords()
}

object TaxGroupsRepositoryV2Impl {
  private val FIELD_ARN = "arn"
  private val FIELD_GROUPNAME = "groupName"
  private val FIELD_SERVICE = "service"

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()
}
