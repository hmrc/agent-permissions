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

class VrnSpec extends AnyFlatSpec with Matchers {

  val reference97 = "101747696"
  val reference9755 = "101747641"

  "isValid" should "return true if the vrn matches regex" in {
    VrnType.isValid(reference97) shouldBe true
    VrnType.isValid(reference9755) shouldBe true
    VrnType.isValid("123456789") shouldBe true
    VrnType.isValid("234567890") shouldBe true
  }

  "isValid" should "return false if the vrn contains non-numbers" in {
    VrnType.isValid("a" + reference97.substring(1)) shouldBe false
    VrnType.isValid("a" + reference9755.substring(1)) shouldBe false
  }

  "isValid" should "return false if the vrn contains more than 9 numbers" in {
    VrnType.isValid("1" + reference97) shouldBe false
    VrnType.isValid("1" + reference9755) shouldBe false
  }

  "isValid" should "return false if the vrn contains fewer than 9 numbers" in {
    VrnType.isValid(reference97.substring(1)) shouldBe false
    VrnType.isValid(reference9755.substring(1)) shouldBe false
  }

  "isValid" should "return false if the vrn contains whitespace" in {
    VrnType.isValid(" " + reference97) shouldBe false
    VrnType.isValid("\t" + reference97) shouldBe false
    VrnType.isValid("         ") shouldBe false
  }
}
