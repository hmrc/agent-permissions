/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.service.userenrolment

import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec

import java.time.LocalDateTime
import scala.util.Random

class UserEnrolmentAssignmentCalculatorSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    def groupName: String = Iterator.continually(Random.nextPrintableChar).filter(_.isLetter).take(10).mkString
    val userA: AgentUser = AgentUser("A", "A")
    val userB: AgentUser = AgentUser("B", "B")
    val userC: AgentUser = AgentUser("C", "C")
    val userD: AgentUser = AgentUser("D", "D")
    val userE: AgentUser = AgentUser("E", "E")

    val enrolment1: Enrolment =
      Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))

    val enrolment2: Enrolment = Enrolment(
      "HMRC-PPT-ORG",
      "Activated",
      "Frank Wright",
      Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
    )

    val enrolment3: Enrolment =
      Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CgtRef", "XMCGTP123456789")))

    val enrolment4: Enrolment =
      Enrolment("HMRC-MTD-IT", "Activated", "MTD IT Client", Seq(Identifier("MTDITID", "236216873678126")))

    val enrolment5: Enrolment =
      Enrolment("HMRC-TERS-ORG", "Activated", "Trust Client", Seq(Identifier("SAUTR", "0123456789")))

    def buildAccessGroup(teamMembers: Set[AgentUser], clients: Set[Enrolment]): AccessGroup =
      AccessGroup(arn, groupName, now, now, userA, userA, Some(teamMembers), Some(clients))

    def buildEnrolmentKey(enrolment: Enrolment): String = EnrolmentKey.enrolmentKeys(enrolment).head

    val userEnrolmentAssignmentCalculator = new UserEnrolmentAssignmentCalculatorImpl

    lazy val now: LocalDateTime = LocalDateTime.now()
  }

  "For group creation" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToCreate: AccessGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(enrolment1, enrolment2, enrolment3))

      val existingAccessGroup1: AccessGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(enrolment1, enrolment2, enrolment4))
      val existingAccessGroup2: AccessGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(enrolment1, enrolment3, enrolment5))

      val expectedAssigns: Set[UserEnrolment] = Set(
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment1)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment2)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment3)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment1)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment2)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment3))
      )

      val expectedUnassigns: Set[UserEnrolment] = Set.empty

      userEnrolmentAssignmentCalculator.forGroupCreation(
        accessGroupToCreate,
        Seq(existingAccessGroup1, existingAccessGroup2)
      ) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns))
    }
  }

  "For group update" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToUpdate: AccessGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(enrolment1, enrolment2, enrolment3))

      val accessGroupToUpdatePreviousVersion: AccessGroup = accessGroupToUpdate.copy(
        teamMembers = Some(Set(userA, userB, userD)),
        clients = Some(Set(enrolment1, enrolment2, enrolment4))
      )

      val existingAccessGroup2: AccessGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(enrolment1, enrolment2, enrolment4))

      val expectedAssigns: Set[UserEnrolment] = Set(
        UserEnrolment(userA.id, buildEnrolmentKey(enrolment3)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment3)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment1)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment2)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment3))
      )

      val expectedUnassigns: Set[UserEnrolment] = Set(
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment4))
      )

      userEnrolmentAssignmentCalculator.forGroupUpdate(
        accessGroupToUpdate,
        Seq(accessGroupToUpdatePreviousVersion, existingAccessGroup2)
      ) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns))
    }
  }

  "For group deletion" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToDelete: AccessGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(enrolment1, enrolment2, enrolment3))

      val existingAccessGroup2: AccessGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(enrolment1, enrolment2, enrolment4))

      val expectedAssigns: Set[UserEnrolment] = Set.empty

      val expectedUnassigns: Set[UserEnrolment] = Set(
        UserEnrolment(userA.id, buildEnrolmentKey(enrolment3)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment1)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment2)),
        UserEnrolment(userB.id, buildEnrolmentKey(enrolment3)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment1)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment2)),
        UserEnrolment(userC.id, buildEnrolmentKey(enrolment3))
      )

      userEnrolmentAssignmentCalculator
        .forGroupDeletion(accessGroupToDelete, Seq(accessGroupToDelete, existingAccessGroup2)) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns))
    }
  }
}
