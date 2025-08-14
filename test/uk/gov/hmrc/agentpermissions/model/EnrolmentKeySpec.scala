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

class EnrolmentKeySpec extends AnyFlatSpec with Matchers {

  "EnrolmentKey" should "generate enrolment keys correctly" in {
    EnrolmentKey.enrolmentKey("HMRC-MTD-IT", "someId") shouldBe "HMRC-MTD-IT~MTDITID~someId"
    EnrolmentKey.enrolmentKey("HMRC-MTD-VAT", "someId") shouldBe "HMRC-MTD-VAT~VRN~someId"
    EnrolmentKey.enrolmentKey("HMRC-TERS-ORG", "someId") shouldBe "HMRC-TERS-ORG~SAUTR~someId"
    EnrolmentKey.enrolmentKey("HMRC-TERSNT-ORG", "someId") shouldBe "HMRC-TERSNT-ORG~URN~someId"
    EnrolmentKey.enrolmentKey("HMRC-CGT-PD", "someId") shouldBe "HMRC-CGT-PD~CGTPDRef~someId"
    EnrolmentKey.enrolmentKey("HMRC-PPT-ORG", "someId") shouldBe "HMRC-PPT-ORG~EtmpRegistrationNumber~someId"
    // TODO intentionally not testing HMRC-CBC-ORG as we need to change it to also include a UTR.
    EnrolmentKey.enrolmentKey("HMRC-CBC-NONUK-ORG", "someId") shouldBe "HMRC-CBC-NONUK-ORG~cbcId~someId"
    EnrolmentKey.enrolmentKey("HMRC-PT", "someId") shouldBe "HMRC-PT~NINO~someId"
    EnrolmentKey.enrolmentKey("HMRC-PILLAR2-ORG", "anId") shouldBe "HMRC-PILLAR2-ORG~PLRID~anId"
    EnrolmentKey.enrolmentKey("HMRC-MTD-IT-SUPP", "someId") shouldBe "HMRC-MTD-IT-SUPP~MTDITID~someId"
    an[Exception] shouldBe thrownBy(EnrolmentKey.enrolmentKey("badServiceId", "someId"))
  }
  it should "extract the service id correctly" in {
    EnrolmentKey.serviceOf("HMRC-MTD-IT~MTDITID~someId") shouldBe "HMRC-MTD-IT"
    EnrolmentKey.serviceOf("HMRC-MTD-VAT~VRN~someId") shouldBe "HMRC-MTD-VAT"
    EnrolmentKey.serviceOf("HMRC-TERS-ORG~SAUTR~someId") shouldBe "HMRC-TERS-ORG"
    EnrolmentKey.serviceOf("HMRC-TERSNT-ORG~URN~someId") shouldBe "HMRC-TERSNT-ORG"
    EnrolmentKey.serviceOf("HMRC-CGT-PD~CGTPDRef~someId") shouldBe "HMRC-CGT-PD"
    EnrolmentKey.serviceOf("HMRC-PPT-ORG~EtmpRegistrationNumber~someId") shouldBe "HMRC-PPT-ORG"
    EnrolmentKey.serviceOf("HMRC-CBC-ORG~UTR~0123456789~cbcId~someId") shouldBe "HMRC-CBC-ORG"
    EnrolmentKey.serviceOf("HMRC-CBC-NONUK-ORG~cbcId~someId") shouldBe "HMRC-CBC-NONUK-ORG"
    EnrolmentKey.serviceOf("HMRC-PILLAR2-ORG~PLRID~someId") shouldBe "HMRC-PILLAR2-ORG"
    EnrolmentKey.serviceOf("HMRC-PT~NINO~someId") shouldBe "HMRC-PT"
    EnrolmentKey.serviceOf("HMRC-MTD-IT-SUPP~MTDITID~someId") shouldBe "HMRC-MTD-IT-SUPP"
  }
  it should "extract the identifiers correctly" in {
    EnrolmentKey.identifiersOf("HMRC-MTD-IT~MTDITID~someId") shouldBe Seq(Identifier("MTDITID", "someId"))
    EnrolmentKey.identifiersOf("HMRC-MTD-VAT~VRN~someId") shouldBe Seq(Identifier("VRN", "someId"))
    EnrolmentKey.identifiersOf("HMRC-TERS-ORG~SAUTR~someId") shouldBe Seq(Identifier("SAUTR", "someId"))
    EnrolmentKey.identifiersOf("HMRC-TERSNT-ORG~URN~someId") shouldBe Seq(Identifier("URN", "someId"))
    EnrolmentKey.identifiersOf("HMRC-CGT-PD~CGTPDRef~someId") shouldBe Seq(Identifier("CGTPDRef", "someId"))
    EnrolmentKey.identifiersOf("HMRC-PPT-ORG~EtmpRegistrationNumber~someId") shouldBe Seq(
      Identifier("EtmpRegistrationNumber", "someId")
    )
    EnrolmentKey.identifiersOf("HMRC-CBC-ORG~UTR~0123456789~cbcId~someId") shouldBe Seq(
      Identifier("UTR", "0123456789"),
      Identifier("cbcId", "someId")
    )
    EnrolmentKey.identifiersOf("HMRC-CBC-NONUK-ORG~cbcId~someId") shouldBe Seq(Identifier("cbcId", "someId"))
    EnrolmentKey.identifiersOf("HMRC-PILLAR2-ORG~PLRID~someId") shouldBe Seq(Identifier("PLRID", "someId"))
    EnrolmentKey.identifiersOf("HMRC-PT~NINO~someId") shouldBe Seq(Identifier("NINO", "someId"))
    EnrolmentKey.identifiersOf("HMRC-MTD-IT-SUPP~MTDITID~someId") shouldBe Seq(Identifier("MTDITID", "someId"))
    an[Exception] shouldBe thrownBy(EnrolmentKey.identifiersOf("HMRC-FAKE-SVC")) // only one part
    an[Exception] shouldBe thrownBy(EnrolmentKey.identifiersOf("HMRC-FAKE-SVC~NINO")) // only two part
    an[Exception] shouldBe thrownBy(
      EnrolmentKey.identifiersOf("HMRC-FAKE-SVC~NINO~AB123456Z~anotherId")
    ) // incorrect number of parts
  }

  it should "build enrolment keys from enrolments correctly" in {
    val vatEnrolment = Enrolment("HMRC-MTD-VAT", "Activated", "Joe", Seq(Identifier("VRN", "123456789")))
    EnrolmentKey.fromEnrolment(vatEnrolment) shouldBe "HMRC-MTD-VAT~VRN~123456789"
    val cbcEnrolment = Enrolment(
      "HMRC-CBC-ORG",
      "Activated",
      "Joe",
      Seq(Identifier("UTR", "0101010101"), Identifier("cbcId", "XACBC0123456789"))
    )
    EnrolmentKey.fromEnrolment(cbcEnrolment) shouldBe "HMRC-CBC-ORG~UTR~0101010101~cbcId~XACBC0123456789"
    val mtdItSuppEnrolment = Enrolment("HMRC-MTD-IT-SUPP", "Activated", "Joe", Seq(Identifier("MTDITID", "234567891")))
    EnrolmentKey.fromEnrolment(mtdItSuppEnrolment) shouldBe "HMRC-MTD-IT-SUPP~MTDITID~234567891"
  }
}
