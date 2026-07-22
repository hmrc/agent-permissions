/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.util.UUID

class GroupIdSpec extends AnyFlatSpec with Matchers {

  "GroupId" should "support random, fromString, fromUuid, value and asUuid" in {
    val uuid = UUID.fromString("00000abc-6789-6789-6789-0000000000aa")

    val groupIdFromString = GroupId.fromString(uuid.toString)
    val groupIdFromUuid = GroupId.fromUuid(uuid)
    val randomGroupId = GroupId.random()

    groupIdFromString.value shouldBe uuid.toString
    groupIdFromString.asUuid shouldBe uuid
    groupIdFromUuid.value shouldBe uuid.toString
    groupIdFromUuid.asUuid shouldBe uuid
    randomGroupId.value should not be empty
  }

  it should "serialise and deserialise via the JSON format" in {
    val uuid = UUID.fromString("00000abc-6789-6789-6789-0000000000aa")
    val groupId = GroupId.fromUuid(uuid)

    val json = Json.toJson(groupId)

    json shouldBe Json.parse(s""""${uuid.toString}"""")
    json.as[GroupId] shouldBe groupId
  }

  it should "bind and unbind via the path binder" in {
    val uuid = UUID.fromString("00000abc-6789-6789-6789-0000000000aa")
    val groupId = GroupId.fromUuid(uuid)
    val binder = summon[play.api.mvc.PathBindable[GroupId]]

    binder.bind("groupId", uuid.toString) shouldBe Right(groupId)
    binder.unbind("groupId", groupId) shouldBe uuid.toString
  }
}
