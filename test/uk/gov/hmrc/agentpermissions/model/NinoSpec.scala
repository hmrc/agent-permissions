/*
 * Copyright 2025 HM Revenue & Customs
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

class NinoSpec extends AnyFlatSpec with Matchers {

  it should "be true for a valid NINO" in {
    NinoType.isValid("PW640851D") shouldBe true
  }

  it should "be false when it has the wrong prefix" in {
    UrnType.isValid("DW640851D") shouldBe false
  }

  it should "be false when it is empty" in {
    UrnType.isValid("") shouldBe false
  }
}
