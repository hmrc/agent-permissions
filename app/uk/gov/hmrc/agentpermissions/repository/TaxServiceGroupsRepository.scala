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
import com.mongodb.client.model.{Collation, IndexOptions}
import com.mongodb.{BasicDBObject, MongoWriteException}
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model._
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, TaxGroup}
import uk.gov.hmrc.agentpermissions.model.SensitiveTaxServiceGroup
import uk.gov.hmrc.agentpermissions.model.SensitiveTaxServiceGroup.encryptAgentUser
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepositoryImpl.{FIELD_ARN, FIELD_GROUPNAME, FIELD_SERVICE, caseInsensitiveCollation}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxServiceGroupsRepositoryImpl])
trait TaxServiceGroupsRepository {
  def findById(id: String): Future[Option[TaxGroup]]

  def get(arn: Arn): Future[Seq[TaxGroup]]

  def get(arn: Arn, groupName: String): Future[Option[TaxGroup]]

  def getByService(arn: Arn, service: String): Future[Option[TaxGroup]]
  def groupExistsForTaxService(arn: Arn, service: String): Future[Boolean]

  def insert(accessGroup: TaxGroup): Future[Option[String]]

  def delete(arn: Arn, groupName: String): Future[Option[Long]]

  def update(arn: Arn, groupName: String, accessGroup: TaxGroup): Future[Option[Long]]

  def addTeamMember(id: String, toAdd: AgentUser): Future[UpdateResult]
}

@Singleton
class TaxServiceGroupsRepositoryImpl @Inject() (
  mongoComponent: MongoComponent,
  crypto: Encrypter with Decrypter
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SensitiveTaxServiceGroup](
      collectionName = "tax-service-groups",
      domainFormat = SensitiveTaxServiceGroup.format(crypto),
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
    ) with TaxServiceGroupsRepository with Logging {

  override def findById(id: String): Future[Option[TaxGroup]] =
    collection
      .find(new BasicDBObject("_id", id))
      .headOption()
      .map(_.map(_.decryptedValue))

  override def get(arn: Arn): Future[Seq[TaxGroup]] =
    collection
      .find(equal(FIELD_ARN, arn.value))
      .collation(caseInsensitiveCollation)
      .collect()
      .toFuture()
      .map(_.map(_.decryptedValue))

  // check usage
  override def get(arn: Arn, groupName: String): Future[Option[TaxGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_GROUPNAME, groupName)))
      .collation(caseInsensitiveCollation)
      .headOption()
      .map(_.map(_.decryptedValue))

  override def getByService(arn: Arn, service: String): Future[Option[TaxGroup]] =
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_SERVICE, service)))
      .collation(caseInsensitiveCollation)
      .headOption()
      .map(_.map(_.decryptedValue))

  def groupExistsForTaxService(arn: Arn, service: String): Future[Boolean] = {
    val svc = if (service == "HMRC-TERSNT-ORG") "HMRC-TERS-ORG" else service // only TERS-ORG are stored
    collection
      .find(and(equal(FIELD_ARN, arn.value), equal(FIELD_SERVICE, svc)))
      .collation(caseInsensitiveCollation)
      .toFuture()
      .map(_.nonEmpty)
  }

  override def insert(accessGroup: TaxGroup): Future[Option[String]] =
    collection
      .insertOne(SensitiveTaxServiceGroup(accessGroup))
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
        SensitiveTaxServiceGroup(accessGroup),
        replaceOptions
      )
      .headOption()
      .map(_.map(result => result.getModifiedCount))

  private lazy val deleteOptions: DeleteOptions = new DeleteOptions().collation(caseInsensitiveCollation)

  private lazy val replaceOptions: ReplaceOptions =
    new ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)

  override def addTeamMember(id: String, toAdd: AgentUser): Future[UpdateResult] = {
    val encryptedAgent = encryptAgentUser(toAdd)(crypto)
    collection
      .updateOne(
        filter = Filters.equal("_id", id),
        update = Updates.addToSet("teamMembers", Codecs.toBson(encryptedAgent))
      )
      .head()
  }
}

object TaxServiceGroupsRepositoryImpl {
  private val FIELD_ARN = "arn"
  private val FIELD_GROUPNAME = "groupName"
  private val FIELD_SERVICE = "service"

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()
}
