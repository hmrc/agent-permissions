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

package uk.gov.hmrc.agentpermissions.service

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.agents.accessgroups.optin._

import java.time.LocalDateTime
import javax.inject.Singleton

@Singleton
class OptinRecordBuilder {

  def forUpdating(
    arn: Arn,
    user: AgentUser,
    maybeExistingOptinRecord: Option[OptinRecord],
    optinEventTypeToMatch: OptinEventType
  ): Option[OptinRecord] =
    maybeExistingOptinRecord match {
      case None =>
        Option(OptinRecord(arn, List(OptinEvent(optinEventTypeToMatch, user, LocalDateTime.now()))))
      case Some(existingOptinRecord) =>
        if (existingOptinRecord.status == optinEventTypeToMatch) {
          None
        } else {
          Option(
            existingOptinRecord.copy(history =
              existingOptinRecord.history :+ OptinEvent(optinEventTypeToMatch, user, LocalDateTime.now())
            )
          )
        }
    }
}
