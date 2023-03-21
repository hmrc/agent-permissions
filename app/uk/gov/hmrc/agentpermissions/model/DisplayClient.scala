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

case class DisplayClient(
  hmrcRef: String,
  name: String,
  taxService: String,
  identifierKey: String,
  alreadyInGroup: Boolean = false,
  selected: Boolean = false
) {
  val enrolmentKey = s"$taxService~$identifierKey~$hmrcRef"
  val id: String = MurmurHash3.stringHash(enrolmentKey).toString
}

case object DisplayClient {
  implicit val format: OFormat[DisplayClient] = Json.format[DisplayClient]

  def fromClient(client: Client, alreadyInGroup: Boolean = false): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val taxService = keyElements.head
    val identifierKey = keyElements(1)
    val hmrcRef = keyElements.last

    DisplayClient(
      hmrcRef = hmrcRef,
      name = client.friendlyName,
      taxService = taxService,
      identifierKey = identifierKey,
      alreadyInGroup = alreadyInGroup
    )
  }
}
