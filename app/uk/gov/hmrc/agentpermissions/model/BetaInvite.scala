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

import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Json, Reads, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

class BetaInvite {

//  sealed trait BetaInviteStatus {
//    val value: String
//  }
//
//  object BetaInviteStatus {
//
//    implicit val reads: Reads[BetaInviteStatus] = {
//      case JsString(DeclinedBetaInvite.value)        => JsSuccess(DeclinedBetaInvite)
//      case JsString(AcceptedBetaInvite.value)         => JsSuccess(AcceptedBetaInvite)
//      case JsString(BetaInvitePending.value)        => JsSuccess(BetaInvitePending)
//      case invalid                                  => JsError(s"Invalid OptedIn value found: $invalid")
//    }
//
//    implicit val writes: Writes[BetaInviteStatus] = (o: BetaInviteStatus) => JsString(o.value)
//  }

  object BetaInviteRecord {

    implicit val readsBetaInviteRecord: Reads[BetaInviteRecord] = Json.reads[BetaInviteRecord]

    implicit val writesBetaInviteRecord: Writes[BetaInviteRecord] = (betaInviteRecord: BetaInviteRecord) =>
      Json.obj(
        fields = "arn" -> betaInviteRecord.arn,
        "hideBetaInvite"  -> betaInviteRecord.hideBetaInvite,
        "numberOfClients" -> betaInviteRecord.numberOfClients,
        "name"            -> betaInviteRecord.name,
        "email"           -> betaInviteRecord.email,
        "phone"           -> betaInviteRecord.phone
      )

    implicit val formatBetaInviteRecord: Format[BetaInviteRecord] =
      Format(readsBetaInviteRecord, writesBetaInviteRecord)
  }

  case class BetaInviteRecord(
    arn: Arn,
    hideBetaInvite: Boolean = false,
    numberOfClients: Option[Int],
    name: Option[String],
    email: Option[String],
    phone: Option[String]
  ) {}
}
