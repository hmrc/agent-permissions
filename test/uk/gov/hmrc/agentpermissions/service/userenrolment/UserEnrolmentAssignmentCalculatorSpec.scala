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

    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")

    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")

    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val clientMtdit: Client = Client(s"$serviceMtdit~$serviceIdentifierKeyMtdit~236216873678126", "Jane Doe")

    val clientTrust: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~0123456789", "Trust Client")

    def buildAccessGroup(teamMembers: Set[AgentUser], clients: Set[Client]): CustomGroup =
      CustomGroup(arn, groupName, now, now, userA, userA, Some(teamMembers), Some(clients))

    val userEnrolmentAssignmentCalculator = new UserEnrolmentAssignmentCalculatorImpl

    lazy val now: LocalDateTime = LocalDateTime.now()
  }

  "For group creation" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToCreate: CustomGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(clientVat, clientPpt, clientCgt))

      val existingAccessGroup1: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientPpt, clientMtdit))
      val existingAccessGroup2: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientCgt, clientTrust))

      val expectedAssigns: Set[UserEnrolment] = Set(
        UserEnrolment(userB.id, clientVat.enrolmentKey),
        UserEnrolment(userB.id, clientPpt.enrolmentKey),
        UserEnrolment(userB.id, clientCgt.enrolmentKey),
        UserEnrolment(userC.id, clientVat.enrolmentKey),
        UserEnrolment(userC.id, clientPpt.enrolmentKey),
        UserEnrolment(userC.id, clientCgt.enrolmentKey)
      )

      val expectedUnassigns: Set[UserEnrolment] = Set.empty

      userEnrolmentAssignmentCalculator.forGroupCreation(
        accessGroupToCreate,
        Seq(existingAccessGroup1, existingAccessGroup2)
      ) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns, arn))
    }
  }

  "For group update" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToUpdate: CustomGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(clientVat, clientPpt, clientCgt))

      val accessGroupToUpdatePreviousVersion: CustomGroup = accessGroupToUpdate.copy(
        groupName = accessGroupToUpdate.groupName.toUpperCase,
        teamMembers = Some(Set(userA, userB, userD)),
        clients = Some(Set(clientVat, clientPpt, clientMtdit))
      )

      val existingAccessGroup2: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientPpt, clientMtdit))

      val expectedAssigns: Set[UserEnrolment] = Set(
        UserEnrolment(userA.id, clientCgt.enrolmentKey),
        UserEnrolment(userB.id, clientCgt.enrolmentKey),
        UserEnrolment(userC.id, clientVat.enrolmentKey),
        UserEnrolment(userC.id, clientPpt.enrolmentKey),
        UserEnrolment(userC.id, clientCgt.enrolmentKey)
      )

      val expectedUnassigns: Set[UserEnrolment] = Set(
        UserEnrolment(userB.id, clientMtdit.enrolmentKey)
      )

      userEnrolmentAssignmentCalculator.forGroupUpdate(
        accessGroupToUpdate,
        Seq(accessGroupToUpdatePreviousVersion, existingAccessGroup2)
      ) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns, arn))
    }
  }

  "For group deletion" should {
    "correctly calculate assigns and unassigns" in new TestScope {
      val accessGroupToDelete: CustomGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(clientVat, clientPpt, clientCgt))

      val existingAccessGroup2: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientPpt, clientMtdit))

      val expectedAssigns: Set[UserEnrolment] = Set.empty

      val expectedUnassigns: Set[UserEnrolment] = Set(
        UserEnrolment(userA.id, clientCgt.enrolmentKey),
        UserEnrolment(userB.id, clientVat.enrolmentKey),
        UserEnrolment(userB.id, clientPpt.enrolmentKey),
        UserEnrolment(userB.id, clientCgt.enrolmentKey),
        UserEnrolment(userC.id, clientVat.enrolmentKey),
        UserEnrolment(userC.id, clientPpt.enrolmentKey),
        UserEnrolment(userC.id, clientCgt.enrolmentKey)
      )

      userEnrolmentAssignmentCalculator
        .forGroupDeletion(accessGroupToDelete, Seq(accessGroupToDelete, existingAccessGroup2)) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, expectedUnassigns, arn))
    }
  }
}
