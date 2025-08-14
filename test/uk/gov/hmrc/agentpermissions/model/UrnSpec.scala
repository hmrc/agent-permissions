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

class UrnSpec extends AnyFlatSpec with Matchers {

  it should "be true for a valid URN" in {
    UrnType.isValid("XXTRUST80000001") shouldBe true
    UrnType.isValid("XXTRUST80000010") shouldBe true
    UrnType.isValid("XXTRUST80000100") shouldBe true
    UrnType.isValid("XXTRUST80001000") shouldBe true
    UrnType.isValid("xxTRUST80001000") shouldBe true
  }

  it should "be false when it has more than 15 digits" in {
    UrnType.isValid("20000000000STUST") shouldBe false
  }

  it should "be false when it is empty" in {
    UrnType.isValid("") shouldBe false
  }

  it should "be false when it has fewer than 15 digits" in {
    UrnType.isValid("kk80000080000") shouldBe false
  }

  it should "be false when it has all numbers" in {
    Urn.isValid(urn = "9999999999999999") shouldBe false
  }

  it should "be false when its all lowercase letters" in {
    Urn.isValid(urn = "sssssssssssssss") shouldBe false
  }
  it should "be false when it has no 2 letters at the start" in {
    Urn.isValid("((TRUST80000001") shouldBe false
  }
  it should "be false when its all uppercase letters" in {
    Urn.isValid(urn = "SSSSSSSSSSSSSSS") shouldBe false
  }

  it should "be false when it starts with 2 digits instead of letters" in {
    Urn.isValid(urn = "33TRUST80001000") shouldBe false
  }
  it should "be false when it has non-alphanumeric characters" in {
    Urn.isValid("200000000!") shouldBe false

  }
}
