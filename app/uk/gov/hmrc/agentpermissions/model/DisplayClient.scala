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

/** copy from Selectable in agent-permissions-frontend * */
case class DisplayClient(
  hmrcRef: String,
  name: String,
  taxService: String,
  enrolmentKeyExtra: String, // not used for display - very hacky!!
  alreadyInGroup: Boolean = false,
  selected: Boolean = false
) {
  // TODO problematic assumption about where the 'key' identifier (hmrcRef) is in an enrolmentKey
  val enrolmentKey: String = if (taxService == "HMRC-CBC-ORG") {
    s"$taxService~cbcId~$hmrcRef~$enrolmentKeyExtra"
  } else { s"$taxService~$enrolmentKeyExtra~$hmrcRef" }
  val id: String = MurmurHash3.stringHash(enrolmentKey).toString
}

/** copy from Selectable in agent-permissions-frontend * */
case object DisplayClient {
  implicit val format: OFormat[DisplayClient] = Json.format[DisplayClient]

  // TODO problematic assumption about where the 'key' identifier (hmrcRef) is in an enrolmentKey
  def fromClient(client: Client, alreadyInGroup: Boolean = false): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val taxService = keyElements.head
    // very hacky!!
    val enrolmentKeyExtra = if (keyElements.head.contains("HMRC-CBC-ORG")) {
      s"${keyElements(3)}~${keyElements(4)}" // saves the UTR for later
    } else keyElements(1)
    val hmrcRef = if (keyElements.head.contains("HMRC-CBC-ORG")) {
      keyElements(2) // cbcId not UTR
    } else keyElements.last

    DisplayClient(
      hmrcRef = hmrcRef,
      name = client.friendlyName,
      taxService = taxService,
      enrolmentKeyExtra = enrolmentKeyExtra,
      alreadyInGroup = alreadyInGroup
    )
  }
}
