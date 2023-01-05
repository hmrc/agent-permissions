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

import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
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

    val client1: Client = Client("HMRC-MTD-VAT~VRN~101747641", "John Innes")
    val client2: Client = Client("HMRC-MTD-VAT~VRN~101746700", "Ann Von-Innes")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val groupId: GroupId = GroupId(arn, groupName)
    val dbId: String = new ObjectId().toHexString

    val taxGroup: TaxServiceAccessGroup = TaxServiceAccessGroup(
      arn,
      "VAT",
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      serviceVat,
      automaticUpdates = true,
      Some(Set(client1, client2))
    )

    val accessGroup: AccessGroup = AccessGroup(
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      Some(Set(client1, client2, clientCgt))
    )

    def groupSummary(
      id: ObjectId = new ObjectId(),
      name: String = groupName,
      isCustomGroup: Boolean = true
    ): AccessGroupSummary =
      AccessGroupSummary(id.toHexString, name, if (isCustomGroup) Some(3) else None, 3, isCustomGroup)

    val taxSummaries = Seq(
      groupSummary(name = "Capital Gains Tax", isCustomGroup = false),
      groupSummary(name = "VAT", isCustomGroup = false)
    )

    val customSummaries = Seq(
      groupSummary(),
      groupSummary(name = "Banana"),
      groupSummary(name = "Fort Oreo-gang")
    )
    val mixedSummaries = Seq(
      groupSummary(),
      groupSummary(name = "VAT", isCustomGroup = false)
    )

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockTaxGroupsRepository: TaxServiceGroupsRepository = mock[TaxServiceGroupsRepository]
    val mockTaxGroupsService: TaxGroupsService = mock[TaxGroupsService]
    val mockCustomGroupsService: AccessGroupsService = mock[AccessGroupsService]

    val mockAuditService: AuditService = mock[AuditService]

    val groupsService: GroupsService =
      new GroupsServiceImpl(
        mockTaxGroupsRepository,
        mockCustomGroupsService,
        mockTaxGroupsService
      )

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockTaxGroupsRepositoryGetByService(
      maybeAccessGroup: Option[TaxServiceAccessGroup],
      service: String = serviceCgt
    ): CallHandler2[Arn, String, Future[Option[TaxServiceAccessGroup]]] =
      (mockTaxGroupsRepository
        .getByService(_: Arn, _: String))
        .expects(arn, service)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxGroupsServiceGetAllGroups(
      groups: Seq[TaxServiceAccessGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[TaxServiceAccessGroup]]] =
      (mockTaxGroupsService
        .getAllTaxServiceGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockCustomGroupsServiceGetAllGroups(
      groups: Seq[AccessGroup]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Seq[AccessGroup]]] =
      (mockCustomGroupsService
        .getAllCustomGroups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(groups))

    def mockCustomGroupsServiceGetGroupSummariesForClient(
      accessGroupSummaries: Seq[AccessGroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForClient(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockCustomGroupsServiceGetGroupSummariesForClientWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForClient(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockCustomGroupsServiceGetGroupSummariesForTeamMember(
      accessGroupSummaries: Seq[AccessGroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockCustomServiceGetGroupSummariesForTeamMemberWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockCustomGroupsService
        .getCustomGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

    def mockTaxGroupsServiceGetGroupSummariesForTeamMember(
      accessGroupSummaries: Seq[AccessGroupSummary]
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockTaxGroupsService
        .getTaxGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful accessGroupSummaries)

    def mockTaxGroupsServiceGetGroupSummariesForTeamMemberWithException(
      ex: Exception
    ): CallHandler3[Arn, String, ExecutionContext, Future[Seq[AccessGroupSummary]]] =
      (mockTaxGroupsService
        .getTaxGroupSummariesForTeamMember(_: Arn, _: String)(_: ExecutionContext))
        .expects(*, *, *)
        .returning(Future.failed(ex))

  }

  "Fetching all groups summaries for ARN" when {

    "both group types found" should {
      "return corresponding summaries sorted by group name" in new TestScope {
        val ag1: AccessGroup = accessGroup
        val tg1: TaxServiceAccessGroup = taxGroup
        val tg2: TaxServiceAccessGroup =
          taxGroup.copy(service = serviceCgt, groupName = "Capital Gains Tax", teamMembers = Some(Set(user3)))

        mockCustomGroupsServiceGetAllGroups(Seq(ag1))
        mockTaxGroupsServiceGetAllGroups(Seq(tg1, tg2))

        groupsService.getAllGroupSummaries(arn).futureValue shouldBe
          Seq(
            AccessGroupSummary(taxGroup._id.toHexString, "Capital Gains Tax", None, 1, isCustomGroup = false),
            AccessGroupSummary(accessGroup._id.toHexString, "some group", Some(3), 3, isCustomGroup = true),
            AccessGroupSummary(taxGroup._id.toHexString, "VAT", None, 3, isCustomGroup = false)
          )
      }
    }

    "no groups found" should {
      "return empty seq" in new TestScope {
        mockCustomGroupsServiceGetAllGroups(Seq.empty)
        mockTaxGroupsServiceGetAllGroups(Seq.empty)

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
      "custom summaries and tax group found" in new TestScope {
        mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
        mockTaxGroupsRepositoryGetByService(Some(taxGroup))

        groupsService.getAllGroupSummariesForClient(arn, clientCgt.enrolmentKey).futureValue shouldBe
          customSummaries ++ Seq(AccessGroupSummary.convertTaxServiceGroup(taxGroup))
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
          Seq(AccessGroupSummary.convertTaxServiceGroup(taxGroup))
      }
    }

  }

}
