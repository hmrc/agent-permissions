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

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIdentifier

sealed abstract class Service(
  val id: String,
  val invitationIdPrefix: Char,
  val enrolmentKey: String,
  val supportedSuppliedClientIdType: ClientIdType[_ <: TaxIdentifier],
  val supportedClientIdType: ClientIdType[_ <: TaxIdentifier]
)

object Service {

  case object MtdIt
      extends Service(
        "HMRC-MTD-IT",
        'A',
        "HMRC-MTD-IT",
        NinoType,
        MtdItIdType
      )

  case object PersonalIncomeRecord
      extends Service(
        "PERSONAL-INCOME-RECORD",
        'B',
        "HMRC-NI",
        NinoType,
        NinoType
      )

  case object Vat
      extends Service(
        "HMRC-MTD-VAT",
        'C',
        "HMRC-MTD-VAT",
        VrnType,
        VrnType
      )

  case object Trust
      extends Service(
        "HMRC-TERS-ORG",
        'D',
        "HMRC-TERS-ORG",
        UtrType,
        UtrType
      )

  case object TrustNT
      extends Service(
        "HMRC-TERSNT-ORG",
        'F',
        "HMRC-TERSNT-ORG",
        UrnType,
        UrnType
      )

  case object CapitalGains
      extends Service(
        "HMRC-CGT-PD",
        'E',
        "HMRC-CGT-PD",
        CgtRefType,
        CgtRefType
      )

  case object Ppt
      extends Service(
        "HMRC-PPT-ORG",
        'G',
        "HMRC-PPT-ORG",
        PptRefType,
        PptRefType
      )

  case object Cbc
      extends Service(
        "HMRC-CBC-ORG",
        'H',
        "HMRC-CBC-ORG",
        CbcIdType,
        CbcIdType
      )

  case object CbcNonUk
      extends Service(
        "HMRC-CBC-NONUK-ORG",
        'J',
        "HMRC-CBC-NONUK-ORG",
        CbcIdType,
        CbcIdType
      )

  case object Pillar2
      extends Service(
        "HMRC-PILLAR2-ORG",
        'K',
        "HMRC-PILLAR2-ORG",
        PlrIdType,
        PlrIdType
      )

  case object MtdItSupp
      extends Service(
        "HMRC-MTD-IT-SUPP",
        'L',
        "HMRC-MTD-IT-SUPP",
        NinoType,
        MtdItIdType
      )

  val supportedServices: Seq[Service] = Seq(
    MtdIt,
    Vat,
    PersonalIncomeRecord,
    Trust,
    TrustNT,
    CapitalGains,
    Ppt,
    Cbc,
    CbcNonUk,
    Pillar2,
    MtdItSupp
  )

  def findById(id: String): Option[Service] = supportedServices.find(_.id == id)

}

sealed abstract class ClientIdType[+T <: TaxIdentifier](
  val clazz: Class[_],
  val id: String,
  val enrolmentId: String,
  val createUnderlying: String => T
) {
  def isValid(value: String): Boolean
}

case object NinoType
    extends ClientIdType(
      classOf[Nino],
      "ni",
      "NINO",
      Nino.apply
    ) {
  override def isValid(value: String): Boolean = Nino.isValid(value)
}

case object MtdItIdType
    extends ClientIdType(
      classOf[MtdItId],
      "MTDITID",
      "MTDITID",
      MtdItId.apply
    ) {
  override def isValid(value: String): Boolean = MtdItId.isValid(value)
}

case object VrnType
    extends ClientIdType(
      classOf[Vrn],
      "vrn",
      "VRN",
      Vrn.apply
    ) {
  override def isValid(value: String): Boolean = Vrn.isValid(value)
}

case object UtrType
    extends ClientIdType(
      classOf[Utr],
      "utr",
      "SAUTR",
      Utr.apply
    ) {
  override def isValid(value: String): Boolean = Utr.isValid(value)
}

case object UrnType
    extends ClientIdType(
      classOf[Urn],
      "urn",
      "URN",
      Urn.apply
    ) {
  override def isValid(value: String): Boolean = Urn.isValid(value)
}

case object CgtRefType
    extends ClientIdType(
      classOf[CgtRef],
      "CGTPDRef",
      "CGTPDRef",
      CgtRef.apply
    ) {
  override def isValid(value: String): Boolean = CgtRef.isValid(value)
}

case object PptRefType
    extends ClientIdType(
      classOf[PptRef],
      "EtmpRegistrationNumber",
      "EtmpRegistrationNumber",
      PptRef.apply
    ) {
  override def isValid(value: String): Boolean = PptRef.isValid(value)
}

case object CbcIdType
    extends ClientIdType(
      classOf[CbcId],
      "cbcId",
      "cbcId",
      CbcId.apply
    ) {
  override def isValid(value: String): Boolean = CbcId.isValid(value)
}

case object PlrIdType
    extends ClientIdType(
      classOf[PlrId],
      "PLRID",
      "PLRID",
      PlrId.apply
    ) {
  override def isValid(value: String): Boolean = PlrId.isValid(value)
}
