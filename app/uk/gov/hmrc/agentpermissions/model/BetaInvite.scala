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

import play.api.libs.json.{Format, Json, Reads, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText, Sensitive}

case class BetaInviteRecord(
  arn: Arn,
  agentUserId: String,
  hideBetaInvite: Boolean = false
)

object BetaInviteRecord {

  implicit val readsBetaInviteRecord: Reads[BetaInviteRecord] = Json.reads[BetaInviteRecord]

  implicit val writesBetaInviteRecord: Writes[BetaInviteRecord] = (betaInviteRecord: BetaInviteRecord) =>
    Json.obj(
      fields = "arn" -> betaInviteRecord.arn,
      "agentUserId"    -> betaInviteRecord.agentUserId,
      "hideBetaInvite" -> betaInviteRecord.hideBetaInvite
    )

  implicit val formatBetaInviteRecord: Format[BetaInviteRecord] =
    Format(readsBetaInviteRecord, writesBetaInviteRecord)
}

class BetaInvite {

  sealed trait BetaInviteStatus {
    val value: Boolean
  }

  case object HideBetaInvite extends BetaInviteStatus {
    override val value = true
  }
  case object ShowBetaInvite extends BetaInviteStatus {
    override val value = false
  }

}
