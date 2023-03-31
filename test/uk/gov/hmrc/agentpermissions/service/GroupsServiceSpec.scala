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

package uk.gov.hmrc.agentpermissions.service

import org.scalamock.handlers.{CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.TaxGroupsRepositoryV2
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class GroupsServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val groupName = "some group"
    val insertedId = "insertedId"
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val user3: AgentUser = AgentUser("user3", "User 3")

    val client1: Client = Client(s"$serviceVat~VRN~101747641", "John Innes")
    val client2: Client = Client(s"$serviceVat~VRN~101746700", "Ann Von-Innes")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")
    val trustClient1: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~1234567890", "George Floyd")
    val trustClient2: Client = Client(s"$serviceNTTrust~URN~XATRUST87165231", "George Corn")

    val taxGroup: TaxGroup = TaxGroup(
      GroupId.random(),
      arn,
      "VAT",
      now,
      now,
      user,
      user,
      Set(user, user1, user2),
      serviceVat,
      automaticUpdates = true,
      Set(client1, client2)
    )

    val accessGroup: CustomGroup = CustomGroup(
      GroupId.random(),
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Set(user, user1, user2),
      Set(client1, client2, clientCgt)
    )

    def groupSummary(
      id: GroupId = GroupId.random(),
      name: String = groupName,
      taxService: Option[String] = None
    ): GroupSummary =
      GroupSummary(id, name, if (taxService.isEmpty) Some(3) else None, 3, taxService)

    val taxSummaries = Seq(
      groupSummary(name = "Capital Gains Tax", taxService = Some(serviceCgt)),
      groupSummary(name = "VAT", taxService = Some(serviceVat))
    )

    val customSummaries = Seq(
      groupSummary(),
      groupSummary(name = "Banana"),
      groupSummary(name = "Fort Oreo-gang")
    )
    val mixedSummaries = Seq(
      groupSummary(),
      groupSummary(name = "VAT", taxService = Some(serviceVat))
    )

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockTaxGroupsRepository: TaxGroupsRepositoryV2 = mock[TaxGroupsRepositoryV2]
    val mockTaxGroupsService: TaxGroupsService = mock[TaxGroupsService]
    val mockCustomGroupsService: CustomGroupsService = mock[CustomGroupsService]

    val mockAuditService: AuditService = mock[AuditService]

    val groupsService: GroupSummaryService =
      new GroupSummaryServiceImpl(
        mockTaxGroupsRepository,
        mockCustomGroupsService,
        mockTaxGroupsService
      )

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockTaxGroupsRepositoryGetByService(
      maybeAccessGroup: Option[TaxGroup],
      service: String = serviceCgt
    ): CallHandler2[Arn, String, Future[Option[TaxGroup]]] =
      (mockTaxGroupsRepository
        .getByService(_: Arn, _: String))
        .expects(arn, service)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxGroupsServiceGetAllGroups(
      groups: Seq[TaxGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[TaxGroup]]] =
      (mockTaxGroupsService
        .getAllTaxServiceGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockTaxGroupsServiceGetClientCountForAllGroups(
      clientsCounts: Map[String, Int]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Map[String, Int]]] =
      (mockTaxGroupsService
        .clientCountForTaxGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(clientsCounts))

    def mockCustomGroupsServiceGetAllGroups(
      groups: Seq[CustomGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[CustomGroup]]] =
      (mockCustomGroupsService
        .getAllCustomGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockCustomGroupsServiceGetGroupSummariesForClient(
      accessGroupSummaries: Seq[GroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForClient(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockCustomGroupsServiceGetGroupSummariesForClientWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForClient(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockCustomGroupsServiceGetGroupSummariesForTeamMember(
      accessGroupSummaries: Seq[GroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockCustomServiceGetGroupSummariesForTeamMemberWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockTaxGroupsServiceGetGroupSummariesForTeamMember(
      accessGroupSummaries: Seq[GroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockTaxGroupsService
        .getTaxGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockTaxGroupsServiceGetGroupSummariesForTeamMemberWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[GroupSummary]]] =
      (mockTaxGroupsService
        .getTaxGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

  }

  "Fetching all groups summaries for ARN" when {

    "both group types found" should {
      "return corresponding summaries sorted by group name" in new TestScope {
        val ag1: CustomGroup = accessGroup
        val tg1: TaxGroup = taxGroup
        val tg2: TaxGroup =
          taxGroup.copy(service = serviceCgt, groupName = "Capital Gains Tax", teamMembers = Set(user3))

        mockCustomGroupsServiceGetAllGroups(Seq(ag1))
        mockTaxGroupsServiceGetAllGroups(Seq(tg1, tg2))
        mockTaxGroupsServiceGetClientCountForAllGroups(Map(serviceVat -> 5, serviceCgt -> 10))

        groupsService.getAllGroupSummaries(arn).futureValue shouldBe
          Seq(
            GroupSummary(
              taxGroup.id,
              "Capital Gains Tax",
              Some(10),
              1,
              taxService = Some(serviceCgt)
            ),
            GroupSummary(accessGroup.id, "some group", Some(3), 3),
            GroupSummary(taxGroup.id, "VAT", Some(5), 3, taxService = Some(serviceVat))
          )
      }
    }

    "no groups found" should {
      "return empty seq" in new TestScope {
        mockCustomGroupsServiceGetAllGroups(Seq.empty)
        mockTaxGroupsServiceGetAllGroups(Seq.empty)
        mockTaxGroupsServiceGetClientCountForAllGroups(Map.empty)

        groupsService.getAllGroupSummaries(arn).futureValue shouldBe Seq.empty
      }
    }
  }

  "Fetching all groups summaries for team member" should {

    "return corresponding summaries" when {
      "summaries found" in new TestScope {
        mockCustomGroupsServiceGetGroupSummariesForTeamMember(customSummaries)
        mockTaxGroupsServiceGetGroupSummariesForTeamMember(taxSummaries)

        groupsService.getAllGroupSummariesForTeamMember(arn, "user3").futureValue shouldBe
          customSummaries ++ taxSummaries
      }
    }
  }

  "Fetching all groups summaries for client" should {

    "return corresponding summaries" when {
      "custom summaries and tax group found" when {
        "client is not a trust client" in new TestScope {
          mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
          mockTaxGroupsRepositoryGetByService(Some(taxGroup))

          groupsService.getAllGroupSummariesForClient(arn, clientCgt.enrolmentKey).futureValue shouldBe
            customSummaries ++ Seq(GroupSummary.of(taxGroup))
        }

        "client is a taxable trust" in new TestScope {
          mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
          mockTaxGroupsRepositoryGetByService(Some(taxGroup), service = "HMRC-TERS")

          groupsService.getAllGroupSummariesForClient(arn, trustClient1.enrolmentKey).futureValue shouldBe
            customSummaries ++ Seq(GroupSummary.of(taxGroup))
        }

        "client is a non-taxable trust" in new TestScope {
          mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
          mockTaxGroupsRepositoryGetByService(Some(taxGroup), service = "HMRC-TERS")

          groupsService.getAllGroupSummariesForClient(arn, trustClient2.enrolmentKey).futureValue shouldBe
            customSummaries ++ Seq(GroupSummary.of(taxGroup))
        }

        "client is excluded from tax group" in new TestScope {
          mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
          mockTaxGroupsRepositoryGetByService(Some(taxGroup), service = serviceVat)

          groupsService.getAllGroupSummariesForClient(arn, client1.enrolmentKey).futureValue shouldBe
            customSummaries
        }
      }

      "custom summaries found but no tax group found" in new TestScope {
        mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
        mockTaxGroupsRepositoryGetByService(None)

        groupsService.getAllGroupSummariesForClient(arn, clientCgt.enrolmentKey).futureValue shouldBe
          customSummaries
      }

      "only tax group found" in new TestScope {
        mockCustomGroupsServiceGetGroupSummariesForClient(Seq.empty)
        mockTaxGroupsRepositoryGetByService(Some(taxGroup))

        groupsService.getAllGroupSummariesForClient(arn, clientCgt.enrolmentKey).futureValue shouldBe
          Seq(GroupSummary.of(taxGroup))
      }
    }

  }

}
