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

class ServiceSpec extends AnyWordSpec with Matchers {

  "Service.findById" should {
    "return the matching service" in {
      Service.findById("HMRC-MTD-IT") shouldBe Some(Service.MtdIt)
      Service.findById("HMRC-CBC-NONUK-ORG") shouldBe Some(Service.CbcNonUk)
    }

    "return none for an unknown service" in {
      Service.findById("NOT-A-SERVICE") shouldBe None
    }
  }

  "Service.supportedServices" should {
    "expose all supported enum values" in {
      Service.supportedServices should contain theSameElementsInOrderAs Seq(
        Service.MtdIt,
        Service.PersonalIncomeRecord,
        Service.Vat,
        Service.Trust,
        Service.TrustNT,
        Service.CapitalGains,
        Service.Ppt,
        Service.Cbc,
        Service.CbcNonUk,
        Service.Pillar2,
        Service.MtdItSupp
      )
    }
  }
}
