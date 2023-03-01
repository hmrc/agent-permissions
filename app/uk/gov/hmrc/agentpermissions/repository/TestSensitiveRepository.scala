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

import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, IndexModel, Indexes}
import org.mongodb.scala.result.InsertOneResult
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import play.shaded.oauth.org.apache.commons.codec.digest.DigestUtils
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/* SearchableSensitiveString (think of a shorter name!) */

case class SearchableSensitiveString(override val decryptedValue: String) extends Sensitive[String] {
  def hash: String = DigestUtils.md5Hex(decryptedValue) /* Substitute for hash function of choice */
}

object SearchableSensitiveString {
  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SearchableSensitiveString] =
    new Format[SearchableSensitiveString] {

      implicit val ssf: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
      def reads(json: JsValue): JsResult[SearchableSensitiveString] = {
        val value = (json \ "encryptedValue").as[SensitiveString]
        JsSuccess(SearchableSensitiveString(value.decryptedValue))
      }
      def writes(o: SearchableSensitiveString): JsValue = // Store a hash alongside the encrypted value
        Json.obj("encryptedValue" -> Json.toJson(SensitiveString(o.decryptedValue)), "hash" -> o.hash)
    }
}

/* Test types */

case class SensitiveBar(barPublic: String, barSensitive: SearchableSensitiveString)

object SensitiveBar {
  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveBar] =
    ((__ \ "barPublic").format[String] ~ (__ \ "barSensitive")
      .format[SearchableSensitiveString])(SensitiveBar.apply, unlift(SensitiveBar.unapply))
}

case class SensitiveFoo(
  id: ObjectId,
  fooPublic: String,
  fooSensitive: SearchableSensitiveString,
  bars: Seq[SensitiveBar]
)

object SensitiveFoo {
  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveFoo] = {
    implicit val oif: Format[ObjectId] = MongoFormats.Implicits.objectIdFormat
    ((__ \ "_id").format[ObjectId] ~ (__ \ "fooPublic").format[String] ~ (__ \ "fooSensitive")
      .format[SearchableSensitiveString] ~ (__ \ "bars")
      .format[Seq[SensitiveBar]])(SensitiveFoo.apply, unlift(SensitiveFoo.unapply))
  }
}

/* Test repository */

@Singleton
class TestSensitiveRepository @Inject() (
  mongoComponent: MongoComponent,
  crypto: Encrypter with Decrypter
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SensitiveFoo](
      collectionName = "test-sensitive",
      domainFormat = SensitiveFoo.format(crypto),
      mongoComponent = mongoComponent,
      indexes = Seq(
        IndexModel(Indexes.ascending("fooPublic")),
        IndexModel(Indexes.ascending("bars.hash")),
        IndexModel(Indexes.ascending("fooSensitive.hash"))
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(
          JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)(implicitly[Format[String]], crypto)
        )
      )
    ) {

  def findByClear(clear: String): Future[Option[SensitiveFoo]] =
    collection
      .find(Filters.equal("fooPublic", clear))
      .headOption()

  def findByCrypted(crypted: SearchableSensitiveString): Future[Option[SensitiveFoo]] =
    collection
      .find(Filters.equal("fooSensitive.hash", crypted.hash))
      .headOption()

  def findBySensitiveFieldWithinArray(crypted: SearchableSensitiveString): Future[Option[SensitiveFoo]] =
    collection
      .find(Filters.equal("bars.barSensitive.hash", crypted.hash))
      .headOption()

  def insert(sensitiveType: SensitiveFoo): Future[Option[InsertOneResult]] =
    collection
      .insertOne(sensitiveType)
      .headOption()
}
