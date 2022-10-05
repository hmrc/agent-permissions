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

package uk.gov.hmrc.agentpermissions.service.audit

import org.bson.types.ObjectId
import uk.gov.hmrc.agentpermissions.BaseSpec

class AccessGroupTeamMembersRemovalSpec extends BaseSpec with AuditTestSupport {

  "Split" should {
    "create chunks correctly" in {
      val accessGroupId = new ObjectId()
      val groupName = "some group"
      val chunkSize = 10

      val accessGroupTeamMembersRemovals =
        AccessGroupTeamMembersRemoval.split(
          "KARN1234567",
          accessGroupId,
          groupName,
          (1 to 49).map(_ => teamMember()).toSet,
          chunkSize
        )

      accessGroupTeamMembersRemovals.size shouldBe 5
      accessGroupTeamMembersRemovals.foreach { accessGroupClientsRemoval =>
        accessGroupClientsRemoval.accessGroupId shouldBe accessGroupId
        accessGroupClientsRemoval.accessGroupName shouldBe groupName
      }
      accessGroupTeamMembersRemovals.head.teamMembers.size shouldBe chunkSize
      accessGroupTeamMembersRemovals(1).teamMembers.size shouldBe chunkSize
      accessGroupTeamMembersRemovals(2).teamMembers.size shouldBe chunkSize
      accessGroupTeamMembersRemovals(3).teamMembers.size shouldBe chunkSize
      accessGroupTeamMembersRemovals(4).teamMembers.size shouldBe 9
    }
  }

}
