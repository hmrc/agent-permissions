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

package uk.gov.hmrc.agentpermissions.service.audit

import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn}
import uk.gov.hmrc.agentpermissions.BaseSpec

import java.time.LocalDateTime

class AccessGroupSplitterSpec extends BaseSpec with AuditTestSupport {

  "Splitter" should {
    "create chunks correctly" in {
      val chunkSize = 50

      val accessGroup: AccessGroup = AccessGroup(
        Arn("KARN1234567"),
        "some group",
        LocalDateTime.now(),
        LocalDateTime.now(),
        teamMember(),
        teamMember(),
        Some((1 to 99).map(_ => teamMember()).toSet),
        Some((1 to 199).map(_ => client()).toSet)
      )

      val chunkedAccessGroups = AccessGroupSplitter.split(accessGroup, chunkSize)

      chunkedAccessGroups.size shouldBe 6

      chunkedAccessGroups.head.clients.get.size shouldBe chunkSize
      chunkedAccessGroups.head.teamMembers shouldBe empty

      chunkedAccessGroups(1).clients.get.size shouldBe chunkSize
      chunkedAccessGroups(1).teamMembers shouldBe empty

      chunkedAccessGroups(2).clients.get.size shouldBe chunkSize
      chunkedAccessGroups(2).teamMembers shouldBe empty

      chunkedAccessGroups(3).clients.get.size shouldBe 49
      chunkedAccessGroups(3).teamMembers shouldBe empty

      chunkedAccessGroups(4).clients shouldBe empty
      chunkedAccessGroups(4).teamMembers.get.size shouldBe chunkSize

      chunkedAccessGroups(5).clients shouldBe empty
      chunkedAccessGroups(5).teamMembers.get.size shouldBe 49
    }
  }

}
