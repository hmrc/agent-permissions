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

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3, CallHandler5}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.models.GroupId
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, TaxGroupsRepositoryV2}
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

    val client1: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
    val client2: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101746700", "Ann Von-Innes")
    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")
    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")
    val clientCbcUk: Client =
      Client(s"$serviceCbc~UTR~1234567890~$serviceIdentifierKeyCbc~XACBC1287165239", "George Candy")
    val trustClient1: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~1234567890", "George Floyd")
    val trustClient2: Client = Client(s"$serviceNTTrust~URN~XATRUST87165231", "George Corn")
    val cbcNonUkClient: Client = Client(s"$serviceCbcNonUk~$serviceIdentifierKeyCbc~XACBC1287165231", "George Corn")

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
      Set(client1, client2, clientCgt, clientCbcUk)
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
    val mockCustomGroupsRepository: CustomGroupsRepositoryV2 = mock[CustomGroupsRepositoryV2]
    val mockTaxGroupsService: TaxGroupsService = mock[TaxGroupsService]
    val mockCustomGroupsService: CustomGroupsService = mock[CustomGroupsService]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockAuditService: AuditService = mock[AuditService]

    val groupsService: GroupsService =
      new GroupsServiceImpl(
        mockTaxGroupsRepository,
        mockCustomGroupsRepository,
        mockUserClientDetailsConnector,
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

    def mockCustomGroupsRepositoryGetAll(
      accessGroups: Seq[CustomGroup]
    ): CallHandler1[Arn, Future[Seq[CustomGroup]]] =
      (mockCustomGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

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

    def mockUserClientDetailsConnectorGetClients(
      maybeClients: Option[Seq[Client]]
    ): CallHandler5[Arn, Boolean, Option[String], HeaderCarrier, ExecutionContext, Future[Option[Seq[Client]]]] =
      (mockUserClientDetailsConnector
        .getClients(_: Arn, _: Boolean, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *, *, *)
        .returning(Future successful maybeClients)

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
            GroupSummary(accessGroup.id, "some group", Some(4), 3),
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

        "client is non uk cbc" in new TestScope {
          mockCustomGroupsServiceGetGroupSummariesForClient(customSummaries)
          mockTaxGroupsRepositoryGetByService(Some(taxGroup), service = "HMRC-CBC")

          groupsService.getAllGroupSummariesForClient(arn, cbcNonUkClient.enrolmentKey).futureValue shouldBe
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

  "Fetching all clients" when {

    "AUCD connector returns nothing" should {
      "return no unassigned clients" in new TestScope {
        mockUserClientDetailsConnectorGetClients(None)
        mockTaxGroupsServiceGetAllGroups(Seq.empty)

        groupsService.getAllClients(arn).futureValue shouldBe
          ClientList(Set.empty, Set.empty)
      }
    }

    "AUCD connector returns something" when {

      "AUCD connector returns empty list of clients" should {
        "return correct clients" in new TestScope {
          mockUserClientDetailsConnectorGetClients(Some(Seq.empty))
          mockTaxGroupsServiceGetAllGroups(Seq.empty)

          groupsService.getAllClients(arn).futureValue shouldBe
            ClientList(Set.empty, Set.empty)
        }
      }

      "AUCD connector returns non-empty list of clients" when {

        "no access groups exist" should {
          "return correct clients" in new TestScope {
            val backendClient1: Client = clientVat.copy(friendlyName = "existing client")

            mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
            mockCustomGroupsRepositoryGetAll(Seq.empty)
            mockTaxGroupsServiceGetAllGroups(Seq.empty)

            groupsService.getAllClients(arn).futureValue shouldBe
              ClientList(Set.empty, Set(backendClient1))
          }
        }

        "access groups exist" when {

          "access group exists whose assigned clients match those returned by AUCD connector" should {
            "return correct clients" in new TestScope {
              val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
              mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
              mockCustomGroupsRepositoryGetAll(Seq(accessGroup))
              mockTaxGroupsServiceGetAllGroups(Seq.empty)

              groupsService.getAllClients(arn).futureValue shouldBe
                ClientList(Set(backendClient1), Set.empty)
            }
          }

          "access group exists whose assigned clients do not match those returned by AUCD connector" should {
            "return correct clients" in new TestScope {
              val backendClient1: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client")
              mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1)))
              mockCustomGroupsRepositoryGetAll(Seq(accessGroup))
              mockTaxGroupsServiceGetAllGroups(Seq.empty)

              groupsService.getAllClients(arn).futureValue shouldBe
                ClientList(Set.empty, Set(backendClient1))
            }
          }
        }
      }
    }
  }

  "Fetching assigned clients" when {
    "access group exists whose assigned clients match some of those returned by AUCD connector" should {
      "return correct assigned clients" in new TestScope {
        val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
        val backendClient2: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockCustomGroupsRepositoryGetAll(Seq(accessGroup))
        mockTaxGroupsServiceGetAllGroups(Seq.empty)

        groupsService.getAssignedClients(arn).futureValue shouldBe
          Set(backendClient1)
      }
    }
  }

  "Fetching unassigned clients" when {
    "access group exists whose assigned clients match some of those returned by AUCD connector" should {
      "return correct unassigned clients" in new TestScope {
        val backendClient1: Client = clientVat.copy(friendlyName = "existing client")
        val backendClient2: Client = Client("HMRC-MTD-VAT~VRN~000000001", "existing client2")
        mockUserClientDetailsConnectorGetClients(Some(Seq(backendClient1, backendClient2)))
        mockCustomGroupsRepositoryGetAll(Seq(accessGroup))
        mockTaxGroupsServiceGetAllGroups(Seq.empty)

        groupsService.getUnassignedClients(arn).futureValue shouldBe
          Set(backendClient2)
      }

      s"do not report as 'unassigned' any clients already in tax service groups" in new TestScope {
        val enrolmentKeyVAT = "HMRC-MTD-VAT~VRN~123456789"
        val enrolmentKeyPPT = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
        val enrolmentKeyTrust = "HMRC-TERS-ORG~SAUTR~1731139143"
        val enrolmentKeyTrustNT = "HMRC-TERSNT-ORG~URN~XATRUST73113914"
        val enrolmentKeyCbcUk = "HMRC-CBC-ORG~UTR~1234567890~cbcId~XACBC1234567890"

        val taxServiceAccessGroupPPT: TaxGroup = taxGroup.copy(service = "HMRC-PPT-ORG")
        // These includes both types of trust or cbc client
        val taxServiceAccessGroupTrust: TaxGroup = taxGroup.copy(service = "HMRC-TERS")
        val taxServiceAccessGroupCbc: TaxGroup = taxGroup.copy(service = "HMRC-CBC")

        mockUserClientDetailsConnectorGetClients(
          Some(
            Seq(
              Client(enrolmentKeyVAT, "foo"),
              Client(enrolmentKeyPPT, "bar"),
              Client(enrolmentKeyTrust, "baz"),
              Client(enrolmentKeyTrustNT, "bazNT"),
              Client(enrolmentKeyCbcUk, "alf")
            )
          )
        )
        mockCustomGroupsRepositoryGetAll(Seq.empty)
        mockTaxGroupsServiceGetAllGroups(
          Seq(taxServiceAccessGroupPPT, taxServiceAccessGroupTrust, taxServiceAccessGroupCbc)
        )

        val result = groupsService.getUnassignedClients(arn).futureValue

        result shouldBe Set(
          Client(enrolmentKeyVAT, "foo")
        ) // don't show the PPT, trust or cbc enrolments as there are already tax service groups for that
      }

      s"DO report as 'unassigned' clients in tax service groups but who are excluded from them" in new TestScope {
        val enrolmentKeyVAT = "HMRC-MTD-VAT~VRN~123456789"
        val enrolmentKeyPPT = "HMRC-PPT-ORG~EtmpRegistrationNumber~XAPPT0000012345"
        val taxServiceAccessGroup =
          taxGroup.copy(service = "HMRC-PPT-ORG", excludedClients = Set(Client(enrolmentKeyPPT, "bar")))

        mockUserClientDetailsConnectorGetClients(
          Some(
            Seq(Client(enrolmentKeyVAT, "foo"), Client(enrolmentKeyPPT, "bar"))
          )
        )
        mockCustomGroupsRepositoryGetAll(Seq.empty)
        mockTaxGroupsServiceGetAllGroups(Seq(taxServiceAccessGroup))

        val result = groupsService.getUnassignedClients(arn).futureValue

        result shouldBe Set(
          Client(enrolmentKeyVAT, "foo"),
          Client(enrolmentKeyPPT, "bar")
        ) // do show the PPT enrolment as it's excluded from the tax service group
      }
    }
  }

}
