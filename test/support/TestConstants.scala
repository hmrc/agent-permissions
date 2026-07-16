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

package uk.gov.hmrc.agentpermissions

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentpermissions.model.Arn
import uk.gov.hmrc.agentpermissions.model.accessgroups.{AgentUser, Client, CustomGroup, TaxGroup}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}

import java.time.LocalDateTime
import java.util.UUID

trait TestConstants extends AnyWordSpecLike with Matchers with ScalaFutures with MockFactory with IntegrationPatience {
  val arn: Arn = Arn("XARN8686099")

  val serviceVat = "HMRC-MTD-VAT"
  val servicePpt = "HMRC-PPT-ORG"
  val serviceCgt = "HMRC-CGT-PD"
  val serviceMtdit = "HMRC-MTD-IT"
  val serviceTrust = "HMRC-TERS-ORG"
  val serviceNTTrust = "HMRC-TERSNT-ORG"
  val serviceCbcNonUk = "HMRC-CBC-NONUK-ORG"
  val serviceCbc = "HMRC-CBC-ORG"
  val trustsRegex = "^HMRC-TERS[A-Z]{0,2}-ORG$"

  val serviceIdentifierKeyVat = "VRN"
  val serviceIdentifierKeyPpt = "EtmpRegistrationNumber"
  val serviceIdentifierKeyCgt = "CgtRef"
  val serviceIdentifierKeyMtdit = "MTDITID"
  val serviceIdentifierKeyTrust = "SAUTR"
  val serviceIdentifierKeyNTTrust = "URN"
  val serviceIdentifierKeyCbc = "cbcId"

  val c1: Client = Client("HMRC-MTD-VAT~VRN~123456789", "vat1")
  val c2: Client = Client("HMRC-MTD-IT~MTDITID~WOHV90190595538", "itsa1")
  val c3: Client = Client("HMRC-TERS-ORG~SAUTR~1731139143", "itsa1")

  val tm1: AgentUser = AgentUser("id1", "tm1")
  val tm2: AgentUser = AgentUser("id2", "tm2")

  val now: LocalDateTime = LocalDateTime.now

  val clients: Set[Client] = Set(c1, c2)
  val teamMembers: Set[AgentUser] = Set(tm1, tm2)

  val customGroup: CustomGroup = CustomGroup(
    id = UUID.randomUUID(),
    arn = arn,
    groupName = "Group 1",
    created = now,
    lastUpdated = now,
    createdBy = tm1,
    lastUpdatedBy = tm1,
    teamMembers = teamMembers,
    clients = clients
  )

  val taxGroup: TaxGroup = TaxGroup(
    id = UUID.randomUUID(),
    arn = arn,
    groupName = "Group 1",
    created = now,
    lastUpdated = now,
    createdBy = tm1,
    lastUpdatedBy = tm1,
    teamMembers = teamMembers,
    service = "HMRC-MTD-VAT",
    automaticUpdates = true,
    excludedClients = Set.empty
  )

  val expectedCount: Map[String, Int] = Map("HMRC-MTD-IT" -> 4, "HMRC-MTD-VAT" -> 3)

  // Note: This is simply a randomly-chosen secret key to run tests
  val aesCrypto: Encrypter & Decrypter =
    SymmetricCryptoFactory.aesCrypto(secretKey = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x")
}
