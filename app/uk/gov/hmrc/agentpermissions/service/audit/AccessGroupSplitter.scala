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

import uk.gov.hmrc.agents.accessgroups.CustomGroup

object AccessGroupSplitter {

  def split(accessGroup: CustomGroup, chunkSize: Int): Seq[CustomGroup] = {

    val chunkedAccessGroupsWithClients = accessGroup.clients
      .grouped(chunkSize)
      .map { chunkedClients =>
        accessGroup.copy(clients = chunkedClients, teamMembers = Set.empty)
      }
      .toSeq

    val chunkedAccessGroupsWithTeamMembers =
      accessGroup.teamMembers
        .grouped(chunkSize)
        .map { chunkedTeamMembers =>
          accessGroup.copy(clients = Set.empty, teamMembers = chunkedTeamMembers)
        }
        .toSeq

    chunkedAccessGroupsWithClients ++ chunkedAccessGroupsWithTeamMembers
  }
}
