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

package uk.gov.hmrc.agentpermissions.service

import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec

import java.time.LocalDateTime

class OptinRecordBuilderSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")

    val optinRecordBuilder = new OptinRecordBuilder
  }

  "OptinRecordBuilder" when {

    s"optinEventType is $OptedIn" when {

      val optinEventType: OptinEventType = OptedIn

      "existing optin record is not present" should {

        s"return optin record having single event of type $OptedIn" in new TestScope {
          val maybeExistingOptinRecord: Option[OptinRecord] = None

          val optinRecord: OptinRecord =
            optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType).get

          optinRecord.arn shouldBe arn
          optinRecord.status shouldBe OptedIn
          optinRecord.history.size shouldBe 1
          val optinEvent: OptinEvent = optinRecord.history.head
          optinEvent.optinEventType shouldBe OptedIn
          optinEvent.user shouldBe user
        }
      }

      s"existing optin record is present whose latest event is of type $OptedIn" should {

        s"return nothing" in new TestScope {
          val now: LocalDateTime = LocalDateTime.now()

          val maybeExistingOptinRecord: Option[OptinRecord] =
            Some(
              OptinRecord(
                arn,
                List(
                  OptinEvent(OptedIn, user, now.minusSeconds(1)),
                  OptinEvent(OptedOut, user, now.minusSeconds(2)),
                  OptinEvent(OptedIn, user, now.minusSeconds(3)),
                  OptinEvent(OptedOut, user, now.minusSeconds(4))
                )
              )
            )

          optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType) shouldBe None
        }
      }

      s"existing optin record is present whose latest event is of type $OptedOut" should {

        s"return optin record having multiple events" in new TestScope {
          val now: LocalDateTime = LocalDateTime.now()

          val maybeExistingOptinRecord: Option[OptinRecord] =
            Some(
              OptinRecord(
                arn,
                List(
                  OptinEvent(OptedOut, user, now.minusSeconds(1)),
                  OptinEvent(OptedIn, user, now.minusSeconds(2)),
                  OptinEvent(OptedOut, user, now.minusSeconds(3)),
                  OptinEvent(OptedIn, user, now.minusSeconds(4))
                )
              )
            )

          val optinRecord: OptinRecord =
            optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType).get

          optinRecord.arn shouldBe arn
          optinRecord.status shouldBe OptedIn

          optinRecord.history.size shouldBe 5
          val optinEvent1: OptinEvent = optinRecord.history.head
          optinEvent1.user shouldBe user
          optinEvent1.optinEventType shouldBe OptedOut
          val optinEvent2: OptinEvent = optinRecord.history(1)
          optinEvent2.user shouldBe user
          optinEvent2.optinEventType shouldBe OptedIn
          val optinEvent3: OptinEvent = optinRecord.history(2)
          optinEvent3.user shouldBe user
          optinEvent3.optinEventType shouldBe OptedOut
          val optinEvent4: OptinEvent = optinRecord.history(3)
          optinEvent4.user shouldBe user
          optinEvent4.optinEventType shouldBe OptedIn
          val optinEvent5: OptinEvent = optinRecord.history(4)
          optinEvent5.user shouldBe user
          optinEvent5.optinEventType shouldBe OptedIn
        }
      }
    }

    s"optinEventType is $OptedOut" when {

      val optinEventType: OptinEventType = OptedOut

      "existing optin record is not present" should {

        s"return optin record having single event of type $OptedOut" in new TestScope {
          val maybeExistingOptinRecord: Option[OptinRecord] = None

          val optinRecord: OptinRecord =
            optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType).get

          optinRecord.arn shouldBe arn
          optinRecord.status shouldBe OptedOut
          optinRecord.history.size shouldBe 1
          val optinEvent: OptinEvent = optinRecord.history.head
          optinEvent.optinEventType shouldBe OptedOut
          optinEvent.user shouldBe user
        }
      }

      s"existing optin record is present whose latest event is of type $OptedOut" should {

        s"return nothing" in new TestScope {
          val now: LocalDateTime = LocalDateTime.now()

          val maybeExistingOptinRecord: Option[OptinRecord] =
            Some(
              OptinRecord(
                arn,
                List(
                  OptinEvent(OptedOut, user, now.minusSeconds(1)),
                  OptinEvent(OptedIn, user, now.minusSeconds(2)),
                  OptinEvent(OptedOut, user, now.minusSeconds(3)),
                  OptinEvent(OptedIn, user, now.minusSeconds(4))
                )
              )
            )

          optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType) shouldBe None
        }
      }

      s"existing optin record is present whose latest event is of type $OptedIn" should {

        s"return optin record having multiple events" in new TestScope {
          val now: LocalDateTime = LocalDateTime.now()

          val maybeExistingOptinRecord: Option[OptinRecord] =
            Some(
              OptinRecord(
                arn,
                List(
                  OptinEvent(OptedIn, user, now.minusSeconds(1)),
                  OptinEvent(OptedOut, user, now.minusSeconds(2)),
                  OptinEvent(OptedIn, user, now.minusSeconds(3)),
                  OptinEvent(OptedOut, user, now.minusSeconds(4))
                )
              )
            )

          val optinRecord: OptinRecord =
            optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, optinEventType).get

          optinRecord.arn shouldBe arn
          optinRecord.status shouldBe OptedOut

          optinRecord.history.size shouldBe 5
          val optinEvent1: OptinEvent = optinRecord.history.head
          optinEvent1.user shouldBe user
          optinEvent1.optinEventType shouldBe OptedIn
          val optinEvent2: OptinEvent = optinRecord.history(1)
          optinEvent2.user shouldBe user
          optinEvent2.optinEventType shouldBe OptedOut
          val optinEvent3: OptinEvent = optinRecord.history(2)
          optinEvent3.user shouldBe user
          optinEvent3.optinEventType shouldBe OptedIn
          val optinEvent4: OptinEvent = optinRecord.history(3)
          optinEvent4.user shouldBe user
          optinEvent4.optinEventType shouldBe OptedOut
          val optinEvent5: OptinEvent = optinRecord.history(4)
          optinEvent5.user shouldBe user
          optinEvent5.optinEventType shouldBe OptedOut
        }
      }
    }
  }

}
