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

import uk.gov.hmrc.agentmtdidentifiers.model.AccessGroup

object AccessGroupSplitter {

  def split(accessGroup: AccessGroup, chunkSize: Int): Seq[AccessGroup] = {

    val chunkedAccessGroupsWithClients = accessGroup.clients.toSeq.flatMap(_.grouped(chunkSize).map { chunkedClients =>
      accessGroup.copy(clients = Option(chunkedClients), teamMembers = None)
    })

    val chunkedAccessGroupsWithTeamMembers =
      accessGroup.teamMembers.toSeq.flatMap(_.grouped(chunkSize).map { chunkedTeamMembers =>
        accessGroup.copy(clients = None, teamMembers = Option(chunkedTeamMembers))
      })

    chunkedAccessGroupsWithClients ++ chunkedAccessGroupsWithTeamMembers
  }
}
