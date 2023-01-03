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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import uk.gov.hmrc.agentmtdidentifiers.model.{UserEnrolment, UserEnrolmentAssignments}

object UserEnrolmentAssignmentsSplitter {

  def split(
    userEnrolmentAssignments: UserEnrolmentAssignments,
    chunkSize: Int
  ): Seq[UserEnrolmentAssignments] = {
    val assignsChunks = userEnrolmentAssignments.assign
      .grouped(chunkSize)
      .map(assignsChunk =>
        UserEnrolmentAssignments(
          assign = assignsChunk,
          unassign = Set.empty[UserEnrolment],
          arn = userEnrolmentAssignments.arn
        )
      )
      .toSeq

    val unassignsChunks = userEnrolmentAssignments.unassign
      .grouped(chunkSize)
      .map(unassignsChunk =>
        UserEnrolmentAssignments(
          assign = Set.empty[UserEnrolment],
          unassign = unassignsChunk,
          arn = userEnrolmentAssignments.arn
        )
      )
      .toSeq

    assignsChunks ++ unassignsChunks
  }

}
