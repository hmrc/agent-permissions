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

class CgtRefSpec extends AnyFlatSpec with Matchers {

  it should "be true for a valid CgtRef" in {
    CgtRefType.isValid("XMCGTP123456789") shouldBe true
  }

  it should "be false when CgtRef's character 2 is lowercase" in {
    CgtRefType.isValid("XmCGTP123456789") shouldBe false
  }

  it should "be false when CgtRef's character 1 is not X" in {
    CgtRefType.isValid("ZMCGTP123456789") shouldBe false
  }

  it should "be false when CgtRef CGTP is lowercase" in {
    CgtRefType.isValid("XMcgtp123456789") shouldBe false
  }

  it should "be false when X[A-Z]CGTP is missing" in {
    CgtRefType.isValid("123456789") shouldBe false
  }

  it should "be false when CgtRef is empty" in {
    CgtRefType.isValid("") shouldBe false
  }

  it should "be false when CgtRef is too short" in {
    CgtRefType.isValid("XMCGTP1234") shouldBe false
  }

  it should "be false when CgtRef is too long" in {
    CgtRefType.isValid("XMCGTP1234567890") shouldBe false
  }

}
