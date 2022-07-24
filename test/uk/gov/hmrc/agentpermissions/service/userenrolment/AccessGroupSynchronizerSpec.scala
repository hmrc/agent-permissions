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

import org.scalamock.handlers.{CallHandler1, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, AssignedClient, Enrolment, GroupDelegatedEnrolments, Identifier, UserEnrolment}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.AccessGroupsRepository
import uk.gov.hmrc.agentpermissions.service.{AccessGroupNotUpdated, AccessGroupUpdated}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AccessGroupSynchronizerSpec extends BaseSpec {

  "Syncing With EACD" when {

    "synchronization happens successfully" should {
      "indicate access groups were updated" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        mockAccessGroupsRepositoryGetAll(accessGroups)
        mockAccessGroupsRepositoryUpdate(Some(1))

        accessGroupSynchronizer
          .syncWithEacd(
            arn,
            GroupDelegatedEnrolments(
              Seq(AssignedClient(enrolmentVat.service, enrolmentVat.identifiers, None, agentUser1.id))
            ),
            agentUser1
          )
          .futureValue shouldBe Seq(AccessGroupUpdated)
      }
    }
  }

  "Calculating user enrolments difference" when {

    "GroupDelegatedEnrolments contains mismatched enrolments than in access group" should {
      "calculate removals correctly" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt))),
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentPpt, enrolmentTrust)))
        )

        val groupDelegatedEnrolments: GroupDelegatedEnrolments = GroupDelegatedEnrolments(
          Seq(
            AssignedClient(enrolmentVat.service, enrolmentVat.identifiers, None, agentUser1.id),
            AssignedClient(enrolmentPpt.service, enrolmentPpt.identifiers, None, "someOtherUser")
          )
        )
        val removalSet: Set[UserEnrolment] = accessGroupSynchronizer
          .calculateRemovalSet(
            accessGroups,
            groupDelegatedEnrolments
          )

        removalSet shouldBe Set(
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentPpt)
          ),
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentCgt)
          ),
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentTrust)
          )
        )
      }
    }
  }

  "Building AccessGroups With Updates" when {

    "removal set contains fewer enrolments than in access groups" should {
      "have corresponding enrolments and users removed" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt))),
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentPpt, enrolmentTrust)))
        )

        val removalSet: Set[UserEnrolment] = Set(
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentPpt)
          ),
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentCgt)
          ),
          UserEnrolment(
            agentUser1.id,
            buildEnrolmentKey(enrolmentTrust)
          )
        )

        val accessGroupsHavingRemovalsApplied: Seq[AccessGroup] =
          accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue

        val firstAccessGroupHavingRemovalsApplied: AccessGroup = accessGroupsHavingRemovalsApplied.head
        firstAccessGroupHavingRemovalsApplied.clients shouldBe Some(Set(enrolmentVat))
        firstAccessGroupHavingRemovalsApplied.teamMembers shouldBe Some(Set.empty)
      }
    }

    "removal set contains an unknown enrolment key" should {
      "not affect existing clients of access group" in new TestScope {
        val removalSet: Set[UserEnrolment] = Set(
          UserEnrolment(
            agentUser1.id,
            "unknown"
          )
        )

        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        val accessGroupsHavingRemovalsApplied: Seq[AccessGroup] =
          accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue

        val firstAccessGroupHavingRemovalsApplied: AccessGroup = accessGroupsHavingRemovalsApplied.head
        firstAccessGroupHavingRemovalsApplied.clients shouldBe Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt))
        firstAccessGroupHavingRemovalsApplied.teamMembers shouldBe Some(Set.empty)
      }
    }

    "removal set contains an unknown user id" should {
      "not affect existing team members of access group" in new TestScope {
        val removalSet: Set[UserEnrolment] = Set(
          UserEnrolment(
            "unknown",
            buildEnrolmentKey(enrolmentPpt)
          )
        )

        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        val accessGroupsHavingRemovalsApplied: Seq[AccessGroup] =
          accessGroupSynchronizer.applyRemovalsOnAccessGroups(accessGroups, removalSet, agentUser1).futureValue

        val firstAccessGroupHavingRemovalsApplied: AccessGroup = accessGroupsHavingRemovalsApplied.head
        firstAccessGroupHavingRemovalsApplied.clients shouldBe Some(Set(enrolmentVat, enrolmentCgt))
        firstAccessGroupHavingRemovalsApplied.teamMembers shouldBe Some(Set(agentUser1))
      }
    }
  }

  "Persisting Access Groups" when {

    "update returns nothing" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        mockAccessGroupsRepositoryUpdate(None)

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
      }
    }

    "update indicates one record was updated in DB" should {
      s"return $AccessGroupUpdated" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        mockAccessGroupsRepositoryUpdate(Some(1))

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupUpdated)
      }
    }

    "update indicates no record was updated in DB" should {
      s"return $AccessGroupNotUpdated" in new TestScope {
        val accessGroups: Seq[AccessGroup] = Seq(
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(enrolmentVat, enrolmentPpt, enrolmentCgt)))
        )

        mockAccessGroupsRepositoryUpdate(Some(0))

        accessGroupSynchronizer.persistAccessGroups(accessGroups).futureValue shouldBe Seq(AccessGroupNotUpdated)
      }
    }
  }

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName")
    val agentUser2: AgentUser = AgentUser("userId2", "userName")
    val now: LocalDateTime = LocalDateTime.now()

    val enrolmentVat: Enrolment =
      Enrolment("HMRC-MTD-VAT", "Activated", "John Innes", Seq(Identifier("VRN", "101747641")))

    val enrolmentPpt: Enrolment = Enrolment(
      "HMRC-PPT-ORG",
      "Activated",
      "Frank Wright",
      Seq(Identifier("EtmpRegistrationNumber", "XAPPT0000012345"))
    )

    val enrolmentCgt: Enrolment =
      Enrolment("HMRC-CGT-PD", "Activated", "George Candy", Seq(Identifier("CGTPDRef", "XMCGTP123456789")))

    val enrolmentMtdit: Enrolment =
      Enrolment("HMRC-MTD-IT", "Activated", "MTD IT Client", Seq(Identifier("MTDITID", "236216873678126")))

    val enrolmentTrust: Enrolment =
      Enrolment("HMRC-TERS-ORG", "Activated", "Trust Client", Seq(Identifier("SAUTR", "0123456789")))

    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val accessGroupSynchronizer = new AccessGroupSynchronizerImpl(mockAccessGroupsRepository)

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Enrolment]]): AccessGroup =
      AccessGroup(arn, groupName, now, now, agentUser1, agentUser1, teamMembers, clients)

    def buildEnrolmentKey(enrolment: Enrolment) =
      s"${enrolment.service}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"

    def mockAccessGroupsRepositoryGetAll(
      accessGroups: Seq[AccessGroup]
    ): CallHandler1[Arn, Future[Seq[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockAccessGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, AccessGroup, Future[Option[Long]]] =
      (mockAccessGroupsRepository
        .update(_: Arn, _: String, _: AccessGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))
  }

}
