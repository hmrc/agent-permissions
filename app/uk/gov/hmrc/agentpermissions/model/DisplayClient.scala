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

package uk.gov.hmrc.agentpermissions.model

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agents.accessgroups.Client

import scala.util.hashing.MurmurHash3

/** copy from Selectable in agent-permissions-frontend **/
case class DisplayClient(
  hmrcRef: String,
  name: String,
  taxService: String,
  enrolmentKeyMiddle: String, // not used for display - hacky!!
  alreadyInGroup: Boolean = false,
  selected: Boolean = false
) {
  val enrolmentKey = s"$taxService~$enrolmentKeyMiddle~$hmrcRef"
  val id: String = MurmurHash3.stringHash(enrolmentKey).toString
}

/** copy from Selectable in agent-permissions-frontend **/
case object DisplayClient {
  implicit val format: OFormat[DisplayClient] = Json.format[DisplayClient]

  // TODO problematic assumption about where the 'key' identifier (hmrcRef) is in an enrolmentKey
  def fromClient(client: Client, alreadyInGroup: Boolean = false): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val taxService = keyElements.head
    // very hacky!!
    val enrolmentKeyMiddle = if (keyElements.head.contains("HMRC-CBC-ORG")) {
      s"${keyElements(1)}~${keyElements(2)}~${keyElements(3)}"
    } else keyElements(1)
    val hmrcRef = keyElements.last

    DisplayClient(
      hmrcRef = hmrcRef,
      name = client.friendlyName,
      taxService = taxService,
      enrolmentKeyMiddle = enrolmentKeyMiddle,
      alreadyInGroup = alreadyInGroup
    )
  }
}
