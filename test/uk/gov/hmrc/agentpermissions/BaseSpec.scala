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

package uk.gov.hmrc.agentpermissions

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}

abstract class BaseSpec extends AnyWordSpecLike with Matchers with ScalaFutures with MockFactory {
  val serviceVat = "HMRC-MTD-VAT"
  val servicePpt = "HMRC-PPT-ORG"
  val serviceCgt = "HMRC-CGT-PD"
  val serviceMtdit = "HMRC-MTD-IT"
  val serviceTrust = "HMRC-TERS-ORG"
  val serviceNTTrust = "HMRC-TERSNT-ORG"
  val trustsRegex = "^HMRC-TERS[A-Z]{0,2}-ORG$"

  val serviceIdentifierKeyVat = "VRN"
  val serviceIdentifierKeyPpt = "EtmpRegistrationNumber"
  val serviceIdentifierKeyCgt = "CgtRef"
  val serviceIdentifierKeyMtdit = "MTDITID"
  val serviceIdentifierKeyTrust = "SAUTR"

  // Note: This is simply a randomly-chosen secret key to run tests
  val aesGcmCrypto: Encrypter with Decrypter =
    SymmetricCryptoFactory.aesGcmCrypto(secretKey = "hWmZq3t6w9zrCeF5JiNcRfUjXn2r5u7x")
}
