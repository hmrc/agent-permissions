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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, UserEnrolment, UserEnrolmentAssignments}
import uk.gov.hmrc.agentpermissions.BaseSpec

import scala.util.Random

class UserEnrolmentAssignmentsSplitterSpec extends BaseSpec {

  val arn: Arn = Arn("KARN1234567")

  "Splitter" when {

    "both assigns and unassigns are empty" should {
      "return empty list" in {
        UserEnrolmentAssignmentsSplitter.split(UserEnrolmentAssignments(Set.empty, Set.empty, arn)) shouldBe Seq.empty
      }
    }

    "assigns and unassigns are non-empty" should {
      "split with correct count" in {
        val maxIndex = 100
        val chunkSize = 10

        val assigns = buildRandomUserEnrolments(maxIndex)
        val unassigns = buildRandomUserEnrolments(maxIndex)

        val splitUserEnrolmentAssignments =
          UserEnrolmentAssignmentsSplitter.split(UserEnrolmentAssignments(assigns, unassigns, arn), chunkSize)

        (roundUp((assigns.size.toDouble) / chunkSize) + roundUp(
          (unassigns.size.toDouble) / chunkSize
        )) shouldBe splitUserEnrolmentAssignments.size
      }
    }
  }

  def buildRandomUserEnrolments(maxIndex: Int): Set[UserEnrolment] = {
    val r = new Random

    for {
      indexTeamMember: Int <- (1 to r.nextInt(maxIndex)).toSet
      indexClient: Int     <- (1 to r.nextInt(maxIndex)).toSet
    } yield UserEnrolment(s"user$indexTeamMember", s"enrolmentKey$indexClient")
  }

  def roundUp(d: Double): Int = math.ceil(d).toInt

}
