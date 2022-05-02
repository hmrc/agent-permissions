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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import java.time.LocalDateTime

sealed trait OptedStatus
case object OptedIn extends OptedStatus
case object OptedOut extends OptedStatus

case class OptedEvent(optedStatus: OptedStatus, userName: String, eventDateTime: LocalDateTime)

case class OptedRecord(arn: Arn, history: List[OptedEvent]) {

  val optedStatus: OptedStatus = history match {
    case Nil => OptedOut
    case events => events.sortWith(_.eventDateTime isAfter _.eventDateTime).head.optedStatus
  }
}
