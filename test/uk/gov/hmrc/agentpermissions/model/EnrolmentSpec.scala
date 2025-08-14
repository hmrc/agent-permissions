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
import play.api.libs.json.Json

class EnrolmentSpec extends AnyFlatSpec with Matchers {

  val testEnrolment: Enrolment = Enrolment(
    service = "HMRC-MTD-IT",
    state = "Active",
    friendlyName = "myName",
    identifiers = List(Identifier("MTDITID", "XX12345"))
  )

  "Enrolment" should "serialise to JSON" in {
    val json = Json.toJson(testEnrolment)
    json.toString shouldBe """{"service":"HMRC-MTD-IT","state":"Active","friendlyName":"myName","identifiers":[{"key":"MTDITID","value":"XX12345"}]}"""
  }

  "Enrolment" should "deserialise from JSON" in {
    val json = Json.toJson(testEnrolment)
    json.as[Enrolment] shouldBe testEnrolment
  }

  "Identifier" should "toString" in {
    val identifier = Identifier("HMRC-MTD-IT", "XX12345")
    identifier.toString shouldBe "HMRC-MTD-IT~XX12345"
  }

  "Identifier's implicit Ordering" should "sort identifiers by key" in {
    val unsortedIdentifiers = Seq(
      Identifier("HMRC-MTD-VAT", "XX12345"),
      Identifier("HMRC-MTD-IT", "XX12345"),
      Identifier("HMRC-CGT-PD", "XX12345")
    )

    unsortedIdentifiers.sorted shouldBe Seq(
      Identifier("HMRC-CGT-PD", "XX12345"),
      Identifier("HMRC-MTD-IT", "XX12345"),
      Identifier("HMRC-MTD-VAT", "XX12345")
    )
  }

}
