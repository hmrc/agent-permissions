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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UtrSpec extends AnyFlatSpec with Matchers {

  it should "be true for a valid UTR" in {
    UtrType.isValid("2000000000") shouldBe true
    UtrType.isValid("9000000001") shouldBe true
    UtrType.isValid("7000000002") shouldBe true
    UtrType.isValid("5000000003") shouldBe true
  }

  it should "be false when it has more than 10 digits" in {
    UtrType.isValid("20000000000") shouldBe false
  }

  it should "be false when it is empty" in {
    UtrType.isValid("") shouldBe false
  }

  it should "be false when it has fewer than 10 digits" in {
    UtrType.isValid("200000") shouldBe false
  }

  it should "be false when it has non-digit characters" in {
    UtrType.isValid("200000000B") shouldBe false
  }

  it should "be false when it has non-alphanumeric characters" in {
    UtrType.isValid("200000000!") shouldBe false
  }

  it should "be false when it false when the modulus checksum doesn't pass" in {
    UtrType.isValid("0123456789") shouldBe false
  }
}
