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

  "For add to group" should {
    "correctly calculate assigns" in new TestScope {
      val accessGroupToUpdate: CustomGroup =
        buildAccessGroup(Set(userA, userB), Set(clientVat, clientPpt, clientCgt))

      val accessGroupToUpdatePreviousVersion: CustomGroup = accessGroupToUpdate.copy(
        groupName = accessGroupToUpdate.groupName.toUpperCase,
        teamMembers = Some(Set(userA, userB, userD)),
        clients = Some(Set(clientVat, clientPpt, clientCgt))
      )

      val existingAccessGroup2: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientPpt, clientMtdit))

      val expectedAssigns: Set[UserEnrolment] = Set(
        UserEnrolment(userD.id, clientCgt.enrolmentKey)
      )

      userEnrolmentAssignmentCalculator.forAddToGroup(
        accessGroupToUpdate.clients.get, // existing clients
        Set(userD), // team member to add
        Seq(accessGroupToUpdatePreviousVersion, existingAccessGroup2),
        GroupId(accessGroupToUpdate.arn, accessGroupToUpdate.groupName)
      ) shouldBe
        Some(UserEnrolmentAssignments(expectedAssigns, Set.empty, arn))
    }
  }

  "For remove from group" should {
    "correctly calculate unassigns" in new TestScope {
      val accessGroupToUpdate: CustomGroup =
        buildAccessGroup(Set(userA, userB, userC), Set(clientVat, clientPpt, clientCgt))

      val accessGroupToUpdatePreviousVersion: CustomGroup = accessGroupToUpdate.copy(
        groupName = accessGroupToUpdate.groupName.toUpperCase,
        teamMembers = Some(Set(userA, userB)),
        clients = Some(Set(clientVat, clientPpt, clientCgt))
      )

      val existingAccessGroup2: CustomGroup =
        buildAccessGroup(Set(userA, userD, userE), Set(clientVat, clientPpt, clientMtdit))

      val expectedUnassigns: Set[UserEnrolment] = Set(
        UserEnrolment(userC.id, clientVat.enrolmentKey),
        UserEnrolment(userC.id, clientPpt.enrolmentKey),
        UserEnrolment(userC.id, clientCgt.enrolmentKey)
      )

      userEnrolmentAssignmentCalculator.forRemoveFromGroup(
        accessGroupToUpdate.clients.get, // existing clients
        Set(userC), // team member to remove
        Seq(accessGroupToUpdatePreviousVersion, existingAccessGroup2),
        GroupId(accessGroupToUpdate.arn, accessGroupToUpdate.groupName)
      ) shouldBe
        Some(UserEnrolmentAssignments(Set.empty, expectedUnassigns, arn))
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

  "For pairUserEnrolments" should {
    "return User Enrolments given set of clients and team members" in new TestScope {
      // given
      val users = Set(userA, userB)
      val clients = Set(clientVat, clientPpt, clientCgt)

      val expectedPairs: Set[UserEnrolment] = Set(
        UserEnrolment(userA.id, clientVat.enrolmentKey),
        UserEnrolment(userA.id, clientPpt.enrolmentKey),
        UserEnrolment(userA.id, clientCgt.enrolmentKey),
        UserEnrolment(userB.id, clientCgt.enrolmentKey),
        UserEnrolment(userB.id, clientVat.enrolmentKey),
        UserEnrolment(userB.id, clientPpt.enrolmentKey)
      )

      // then
      userEnrolmentAssignmentCalculator.pairUserEnrolments(users, clients) shouldBe expectedPairs
    }
  }

  "For assessUserEnrolmentPairs" should {
    "return correct User Enrolment Assignments" when {

      "no found pairs in other access groups" when {
        "net change is assigns" in new TestScope {
          // given
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )

          val expectedAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(maxNetChange, Set.empty, arn)

          // then
          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(arn, None, maxNetChange, isNetChangeAssign = true) shouldBe expectedAssignments
        }

        "net change is NOT assigns" in new TestScope {
          // given
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )

          val expectedAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(Set.empty, maxNetChange, arn)

          // then
          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(arn, None, maxNetChange, isNetChangeAssign = false) shouldBe expectedAssignments
        }
      }

      "found some pairs in other access groups" when {
        "net change is assigns" in new TestScope {
          // given
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )
          val foundPairs: Set[UserEnrolment] = maxNetChange.take(2)

          val expectedAssignments: UserEnrolmentAssignments =
            UserEnrolmentAssignments(maxNetChange -- foundPairs, Set.empty, arn)

          // then
          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(
              arn,
              Some(foundPairs),
              maxNetChange,
              isNetChangeAssign = true
            ) shouldBe expectedAssignments
        }

        "net change is NOT assigns" in new TestScope {
          // given
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )
          val foundPairs: Set[UserEnrolment] = maxNetChange.take(2)

          val expectedAssignments: UserEnrolmentAssignments =
            UserEnrolmentAssignments(Set.empty, maxNetChange -- foundPairs, arn)

          // then
          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(
              arn,
              Some(foundPairs),
              maxNetChange,
              isNetChangeAssign = false
            ) shouldBe expectedAssignments
        }
      }

      "found all pairs in other access groups" when {
        "net change is assigns" in new TestScope {
          // given
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )
          val expectedAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(Set.empty, Set.empty, arn)

          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(
              arn,
              Some(maxNetChange),
              maxNetChange,
              isNetChangeAssign = true
            ) shouldBe expectedAssignments
        }

        "net change is NOT assigns" in new TestScope {
          val maxNetChange: Set[UserEnrolment] = Set(
            UserEnrolment(userA.id, clientVat.enrolmentKey),
            UserEnrolment(userA.id, clientPpt.enrolmentKey),
            UserEnrolment(userA.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientCgt.enrolmentKey),
            UserEnrolment(userB.id, clientVat.enrolmentKey),
            UserEnrolment(userB.id, clientPpt.enrolmentKey)
          )
          val expectedAssignments: UserEnrolmentAssignments = UserEnrolmentAssignments(Set.empty, Set.empty, arn)

          userEnrolmentAssignmentCalculator
            .assessUserEnrolmentPairs(
              arn,
              Some(maxNetChange),
              maxNetChange,
              isNetChangeAssign = false
            ) shouldBe expectedAssignments

        }
      }
    }
  }
}
