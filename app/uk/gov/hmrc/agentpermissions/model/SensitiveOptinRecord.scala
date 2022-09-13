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

package uk.gov.hmrc.agentpermissions.model

import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.OptinRecord
import uk.gov.hmrc.crypto._

/** Variant of OptinRecord suitable to be stored partially encrypted in Mongo. (APB-6461)
  */
case class SensitiveOptinRecord(override val decryptedValue: OptinRecord) extends Sensitive[OptinRecord]

object SensitiveOptinRecord {
  def encryptFields(optinRecord: OptinRecord)(implicit crypto: Encrypter): OptinRecord = optinRecord.copy(
    history = optinRecord.history.map(event =>
      event.copy(
        user = event.user.copy(
          id = crypto.encrypt(PlainText(event.user.id)).value,
          name = crypto.encrypt(PlainText(event.user.name)).value
        )
      )
    )
  )
  def decryptFields(securedOptinRecord: OptinRecord)(implicit crypto: Decrypter): OptinRecord = securedOptinRecord.copy(
    history = securedOptinRecord.history.map(event =>
      event.copy(
        user = event.user.copy(
          id = crypto.decrypt(Crypted(event.user.id)).value,
          name = crypto.decrypt(Crypted(event.user.name)).value
        )
      )
    )
  )

  implicit def format(implicit crypto: Encrypter with Decrypter): Format[SensitiveOptinRecord] =
    new Format[SensitiveOptinRecord] {
      def reads(json: JsValue): JsResult[SensitiveOptinRecord] =
        Json.fromJson[OptinRecord](json).map(o => SensitiveOptinRecord(decryptFields(o)))
      def writes(o: SensitiveOptinRecord): JsValue =
        Json.toJson(encryptFields(o.decryptedValue))
    }
}
