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
import play.api.libs.json.{JsString, Json, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.Client

case class AccessGroupClientsRemoval(accessGroupId: ObjectId, accessGroupName: String, clients: Set[Client])

object AccessGroupClientsRemoval {
  implicit val objectIdWrites: Writes[ObjectId] = Writes[ObjectId]((o: ObjectId) => JsString(o.toHexString))
  implicit val writes: Writes[AccessGroupClientsRemoval] = Json.writes[AccessGroupClientsRemoval]

  def split(
    accessGroupId: ObjectId,
    groupName: String,
    clientsToRemove: Set[Client],
    chunkSize: Int
  ): Seq[AccessGroupClientsRemoval] =
    clientsToRemove
      .grouped(chunkSize)
      .map { chunkedClients =>
        AccessGroupClientsRemoval(accessGroupId, groupName, chunkedClients)
      }
      .toSeq
}
