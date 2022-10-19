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
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.agentpermissions.model.{BetaInvite, BetaInviteRecord}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BetaInviteRepositoryImpl])
trait BetaInviteRepository {
  def get(agentUser: AgentUser): Future[Option[BetaInviteRecord]]
  def upsert(betaInviteRecord: BetaInviteRecord): Future[Option[UpsertType]]
}

/** Note: This implementation has no encrypted fields in mongo.
  */
@Singleton
class BetaInviteRepositoryImpl @Inject() (
  mongoComponent: MongoComponent,
  crypto: Encrypter with Decrypter
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[BetaInviteRecord](
      collectionName = "beta-invite",
      domainFormat = BetaInviteRecord.formatBetaInviteRecord,
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(ascending("arn"), new IndexOptions().name("arnIdx").unique(true))
      )
    ) with BetaInviteRepository with Logging {

  def get(agentUser: AgentUser): Future[Option[BetaInviteRecord]] =
    collection.find(equal("agentUserId", agentUser.id)).headOption().map(_.map(_.copy()))

  def upsert(betaInviteRecord: BetaInviteRecord): Future[Option[UpsertType]] =
    collection
      .replaceOne(equal("agentUserId", betaInviteRecord.agentUserId), betaInviteRecord, upsertOptions)
      .headOption()
      .map(
        _.map(result =>
          result.getModifiedCount match {
            case 0L => RecordInserted(result.getUpsertedId.asObjectId().getValue.toString)
            case 1L => RecordUpdated
            case x  => throw new RuntimeException(s"Update modified count should not have been $x")
          }
        )
      )

  private def upsertOptions = new ReplaceOptions().upsert(true)
}
