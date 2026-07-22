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

package uk.gov.hmrc.agentpermissions.models

import play.api.libs.json.{Format, JsResult, JsString}
import uk.gov.hmrc.agentpermissions.binders.SimpleObjectBinder

import java.util.UUID

opaque type GroupId = UUID

object GroupId {
  def random(): GroupId = UUID.randomUUID()

  def fromString(s: String): GroupId = UUID.fromString(s)

  def fromUuid(uuid: UUID): GroupId = uuid

  extension (groupId: GroupId) {
    def asUuid: UUID = groupId
    def value: String = groupId.toString
  }

  given Format[GroupId] with {
    override def writes(groupId: GroupId): JsString = JsString(groupId.value)

    override def reads(json: play.api.libs.json.JsValue): JsResult[GroupId] =
      json.validate[String].map(fromString)
  }

  given play.api.mvc.PathBindable[GroupId] =
    new SimpleObjectBinder[GroupId](fromString, _.value)
}
