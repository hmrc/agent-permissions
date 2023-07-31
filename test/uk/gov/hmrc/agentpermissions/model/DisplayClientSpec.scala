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
import uk.gov.hmrc.agents.accessgroups.Client

class DisplayClientSpec extends AnyWordSpec with Matchers {

  "DisplayClient" should {
    "be converted from Client" when {
      "client is cbc uk" in {
        val cbcUkClient = Client("HMRC-CBC-ORG~UTR~1234567890~cbcId~XACBC123456789012", "Jelly Incorporated")

        // when
        val dc = DisplayClient.fromClient(cbcUkClient)

        val expectedDc =
          DisplayClient("XACBC123456789012", "Jelly Incorporated", "HMRC-CBC-ORG", "UTR~1234567890~cbcId")

        // then
        dc shouldBe expectedDc
      }

      "client has no friendly name" in {
        val vatClient = Client("HMRC-MTD-VAT~VRN~123456789", "")

        // when
        val dc = DisplayClient.fromClient(vatClient)

        val expectedDc = DisplayClient("123456789", "", "HMRC-MTD-VAT", "VRN")

        // then
        dc shouldBe expectedDc
      }

    }

  }
}
