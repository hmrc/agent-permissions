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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PlrIdSpec extends AnyWordSpec with Matchers {

  "Pillar2Id" should {
    "be valid if matching the regex" in {
      PlrIdType.isValid("XBPLR1234567890") shouldBe true
    }

    "invalid if more than 15 characters" in {
      PlrIdType.isValid("XBPLR1234567890125637") shouldBe false
    }

    "invalid if less than 15 characters" in {
      PlrIdType.isValid("XBPLR12345") shouldBe false
    }

    "invalid if characters don't match regex" in {
      PlrIdType.isValid("Xdefinitely-not") shouldBe false
    }

    "invalid if empty" in {
      PlrIdType.isValid("") shouldBe false
    }
  }
}
