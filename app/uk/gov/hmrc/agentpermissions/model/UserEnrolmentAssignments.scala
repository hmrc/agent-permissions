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

package uk.gov.hmrc.agentpermissions.model

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

case class UserEnrolment(userId: String, enrolmentKey: String)

object UserEnrolment {
  implicit val formats: Format[UserEnrolment] = Json.format
}

/** Represents the user/client combinations to assign and unassign in EACD.
  * @param assign
  *   combinations to assign using ES11 API
  * @param unassign
  *   combinations to unassign using ES12 API
  */
case class UserEnrolmentAssignments(assign: Set[UserEnrolment], unassign: Set[UserEnrolment], arn: Arn)

object UserEnrolmentAssignments {
  implicit val formats: Format[UserEnrolmentAssignments] = Json.format
}
