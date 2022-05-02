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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import java.time.LocalDateTime

class OptedRecordSpec extends AnyWordSpecLike with Matchers {

  s"$OptedRecord status" when {

    val arn = Arn("KARN1234567")
    val user = "user"

    "no events exist" should {

      s"be $OptedOut" in {
        withOptedRecord(List.empty).optedStatus shouldBe OptedOut
      }
    }

    "only a single opted event exists" when {

      s"has status $OptedIn" should {
        s"be $OptedIn" in {
          withOptedRecord(List(OptedIn -> LocalDateTime.now())).optedStatus shouldBe OptedIn
        }
      }

      s"has status $OptedOut" should {
        s"be $OptedOut" in {
          withOptedRecord(List(OptedOut -> LocalDateTime.now())).optedStatus shouldBe OptedOut
        }
      }
    }

    "multiple opted events exist" when {

      s"has latest $OptedIn event" should {
        s"be $OptedIn" in {
          val now = LocalDateTime.now()

          withOptedRecord(List(
            OptedIn -> now.minusDays(1),
            OptedOut -> now.minusSeconds(1),
            OptedIn -> now)).optedStatus shouldBe OptedIn
        }
      }

      s"has latest $OptedOut event" should {
        s"be $OptedOut" in {
          val now = LocalDateTime.now()

          withOptedRecord(List(
            OptedOut -> now.minusDays(1),
            OptedIn -> now.minusNanos(1000),
            OptedOut -> now)).optedStatus shouldBe OptedOut
        }
      }
    }

    def withOptedRecord(mapStatusToEpoch: List[(OptedStatus, LocalDateTime)]): OptedRecord = {
      OptedRecord(arn, mapStatusToEpoch.map { case (optedStatus, epoch) => OptedEvent(optedStatus, user, epoch) })
    }

  }
}
