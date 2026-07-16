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

  it should "be true for valid UTRs" in {
    Utr.isValid("2000000000") shouldBe true
    Utr.isValid("9000000001") shouldBe true
    Utr.isValid("7000000002") shouldBe true
    Utr.isValid("5000000003") shouldBe true
  }

  it should "be false for invalid UTRs" in {
    Utr.isValid("20000000000") shouldBe false
    Utr.isValid("") shouldBe false
    Utr.isValid("200000") shouldBe false
    Utr.isValid("200000000B") shouldBe false
    Utr.isValid("200000000!") shouldBe false
    Utr.isValid("0123456789") shouldBe false
  }
}
