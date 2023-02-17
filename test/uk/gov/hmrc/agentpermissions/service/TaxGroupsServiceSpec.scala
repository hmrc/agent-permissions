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

import com.mongodb.client.result.UpdateResult
import org.bson.types.ObjectId
import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepository
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class TaxGroupsServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val groupName = "some group"
    val insertedId = "insertedId"
    val user1: AgentUser = AgentUser("user1", "User 1")
    val user2: AgentUser = AgentUser("user2", "User 2")
    val user3: AgentUser = AgentUser("user3", "User 3")
    val vatService: String = "HMRC-MTD-VAT"
    val client1: Client = Client(s"$vatService~VRN~101747641", "John Innes")
    val client2: Client = Client(s"$vatService~VRN~101746700", "Ann Von-Innes")

    val groupId: GroupId = GroupId(arn, groupName)
    val dbId: String = new ObjectId().toHexString

    val taxGroup: TaxGroup = TaxGroup(
      arn,
      groupName,
      now,
      now,
      user,
      user,
      Some(Set(user, user1, user2)),
      vatService,
      automaticUpdates = true,
      Some(Set(client1, client2))
    )

    val fullCountMap = Map(
      "HMRC-MTD-VAT"    -> 2,
      "HMRC-CGT-PD"     -> 3,
      "HMRC-PPT-ORG"    -> 4,
      "HMRC-MTD-IT"     -> 5,
      "HMRC-TERS-ORG"   -> 6,
      "HMRC-TERSNT-ORG" -> 1
    )

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockAUCDConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockTaxServiceGroupsRepository: TaxServiceGroupsRepository = mock[TaxServiceGroupsRepository]
    val mockAuditService: AuditService = mock[AuditService]

    val taxGroupsService: TaxGroupsService =
      new TaxGroupsServiceImpl(
        mockTaxServiceGroupsRepository,
        mockAUCDConnector
      )

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockAUCDGetClientCount(
      clientCountMap: Option[Map[String, Int]]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Map[String, Int]]]] =
      (mockAUCDConnector
        .clientCountByTaxService(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future.successful(clientCountMap))

    def mockTaxGroupsRepositoryGet(
      maybeAccessGroup: Option[TaxGroup]
    ): CallHandler2[Arn, String, Future[Option[TaxGroup]]] =
      (mockTaxServiceGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxGroupsRepositoryGetByService(
      maybeAccessGroup: Option[TaxGroup]
    ): CallHandler2[Arn, String, Future[Option[TaxGroup]]] =
      (mockTaxServiceGroupsRepository
        .getByService(_: Arn, _: String))
        .expects(arn, vatService)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxGroupsRepositoryGetById(
      maybeAccessGroup: Option[TaxGroup]
    ): CallHandler1[String, Future[Option[TaxGroup]]] =
      (mockTaxServiceGroupsRepository
        .findById(_: String))
        .expects(dbId)
        .returning(Future.successful(maybeAccessGroup))

    def mockTaxGroupsRepositoryGetAll(
      accessGroups: Seq[TaxGroup]
    ): CallHandler1[Arn, Future[Seq[TaxGroup]]] =
      (mockTaxServiceGroupsRepository
        .get(_: Arn))
        .expects(arn)
        .returning(Future.successful(accessGroups))

    def mockTaxGroupsRepositoryGroupExistsForService(
      service: String,
      result: Boolean
    ): CallHandler2[Arn, String, Future[Boolean]] =
      (mockTaxServiceGroupsRepository
        .groupExistsForTaxService(_: Arn, _: String))
        .expects(arn, service)
        .returning(Future.successful(result))

    def mockAddTeamMemberToGroup(
      groupId: String,
      member: AgentUser,
      updatedCount: Int = 1
    ): CallHandler2[String, AgentUser, Future[UpdateResult]] =
      (mockTaxServiceGroupsRepository
        .addTeamMember(_: String, _: AgentUser))
        .expects(groupId, member)
        .returning(Future.successful(UpdateResult.acknowledged(updatedCount, updatedCount, null)))

    def mockTaxServiceGroupsRepositoryInsert(
      accessGroup: TaxGroup,
      maybeCreationId: Option[String]
    ): CallHandler1[TaxGroup, Future[Option[String]]] =
      (mockTaxServiceGroupsRepository
        .insert(_: TaxGroup))
        .expects(accessGroup)
        .returning(Future.successful(maybeCreationId))

    def mockTaxServiceGroupsRepositoryDelete(
      maybeDeletedCount: Option[Long]
    ): CallHandler2[Arn, String, Future[Option[Long]]] =
      (mockTaxServiceGroupsRepository
        .delete(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeDeletedCount))

    def mockTaxServiceGroupsRepositoryUpdate(
      maybeModifiedCount: Option[Long]
    ): CallHandler3[Arn, String, TaxGroup, Future[Option[Long]]] =
      (mockTaxServiceGroupsRepository
        .update(_: Arn, _: String, _: TaxGroup))
        .expects(arn, groupName, *)
        .returning(Future.successful(maybeModifiedCount))

  }

  "Calling create" when {

    "group of that service already exists" should {
      s"return $TaxServiceGroupExistsForCreation" in new TestScope {
        mockTaxGroupsRepositoryGetByService(Some(taxGroup))

        taxGroupsService
          .create(taxGroup)
          .futureValue shouldBe TaxServiceGroupExistsForCreation
      }
    }

    "group of that name does not already exist" when {

      "insert calls returns nothing" should {
        s"return $TaxServiceGroupNotCreated" in new TestScope {
          mockTaxGroupsRepositoryGetByService(None)

          mockTaxServiceGroupsRepositoryInsert(taxGroup, None)

          taxGroupsService
            .create(taxGroup)
            .futureValue shouldBe TaxServiceGroupNotCreated
        }
      }

      s"insert calls returns an id" should {
        s"return $TaxServiceGroupCreated" in new TestScope {
          mockTaxGroupsRepositoryGetByService(None)
          mockTaxServiceGroupsRepositoryInsert(taxGroup, Some(insertedId))

          taxGroupsService
            .create(taxGroup)
            .futureValue shouldBe TaxServiceGroupCreated(insertedId)
        }
      }

    }
  }

  "Fetching all groups" when {

    "Tax Service groups exist" should {
      "return corresponding groups" in new TestScope {
        mockTaxGroupsRepositoryGetAll(Seq(taxGroup))

        taxGroupsService.getAllTaxServiceGroups(arn).futureValue shouldBe
          Seq(taxGroup)
      }
    }

    "no groups found" should {
      "return no Tax Service groups" in new TestScope {
        mockTaxGroupsRepositoryGetAll(Seq.empty)

        taxGroupsService.getAllTaxServiceGroups(arn).futureValue shouldBe
          Seq.empty
      }
    }
  }

  "Fetching group by group id" when {

    "group exists" should {
      "return corresponding group" in new TestScope {
        mockTaxGroupsRepositoryGet(Some(taxGroup))

        taxGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(taxGroup)
      }
    }

    "group does not exists" should {
      "return no group" in new TestScope {
        mockTaxGroupsRepositoryGet(None)

        taxGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          None
      }
    }

    "group with no team members" should {
      "return the group" in new TestScope {
        mockTaxGroupsRepositoryGet(Some(taxGroup.copy(teamMembers = None)))

        taxGroupsService.get(GroupId(arn, groupName)).futureValue shouldBe
          Some(taxGroup.copy(teamMembers = None))
      }
    }
  }

  "Fetching group by arn and service id" when {

    "group exists" should {
      "return corresponding group" in new TestScope {
        mockTaxGroupsRepositoryGetByService(Some(taxGroup))

        taxGroupsService.get(arn, vatService).futureValue shouldBe
          Some(taxGroup)
      }
    }

    "group does not exists" should {
      "return no group" in new TestScope {
        mockTaxGroupsRepositoryGetByService(None)

        taxGroupsService.get(arn, vatService).futureValue shouldBe
          None
      }
    }

    "group with no team members" should {
      "return the group" in new TestScope {
        mockTaxGroupsRepositoryGetByService(Some(taxGroup.copy(teamMembers = None)))

        taxGroupsService.get(arn, vatService).futureValue shouldBe
          Some(taxGroup.copy(teamMembers = None))
      }
    }
  }

  "Fetching group by id" when {

    "group exists" should {
      "return corresponding summaries" in new TestScope {
        mockTaxGroupsRepositoryGetById(Some(taxGroup))

        taxGroupsService.getById(dbId).futureValue shouldBe
          Some(taxGroup)
      }
    }
  }

  "Fetching groups for team member" when {

    "groups found" should {
      "return corresponding summaries" in new TestScope {

        val ag1: TaxGroup = taxGroup
        val ag2: TaxGroup = taxGroup.copy(groupName = "group 2", teamMembers = Some(Set(user3)))

        mockTaxGroupsRepositoryGetAll(Seq(ag1, ag2))

        taxGroupsService.getTaxGroupSummariesForTeamMember(arn, "user3").futureValue shouldBe
          Seq(GroupSummary(ag2._id.toHexString, "group 2", None, 1, taxService = Some(serviceVat)))
      }
    }
  }

  "Deleting group" when {

    "DB call to delete group returns nothing" should {
      s"return $TaxServiceGroupNotDeleted" in new TestScope {
        mockTaxServiceGroupsRepositoryDelete(None)

        taxGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupNotDeleted
      }
    }

    "DB call to delete group returns some value" when {

      "call to delete group indicates no record was deleted" should {
        s"return $TaxServiceGroupNotDeleted" in new TestScope {
          mockTaxServiceGroupsRepositoryDelete(Some(0L))

          taxGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupNotDeleted
        }
      }

      "call to delete group indicates one record was deleted" should {
        s"return $TaxServiceGroupDeleted" in new TestScope {
          mockTaxServiceGroupsRepositoryDelete(Some(1L))

          taxGroupsService.delete(groupId, user).futureValue shouldBe TaxServiceGroupDeleted
        }
      }
    }

  }

  "Updating group" when {

    "DB call to update group returns nothing" should {
      s"return $TaxServiceGroupNotUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(None)

        taxGroupsService.update(groupId, taxGroup, user).futureValue shouldBe TaxServiceGroupNotUpdated
      }
    }

    "DB call to update group indicates no record was updated" should {
      s"return $TaxServiceGroupNotUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(Some(0))

        taxGroupsService.update(groupId, taxGroup, user).futureValue shouldBe TaxServiceGroupNotUpdated
      }
    }

    "DB call to update group indicates one record was updated" should {
      s"return $TaxServiceGroupUpdated" in new TestScope {
        mockTaxServiceGroupsRepositoryUpdate(Some(1))

        taxGroupsService.update(groupId, taxGroup, user).futureValue shouldBe TaxServiceGroupUpdated
      }
    }

  }

  private val VAT = "HMRC-MTD-VAT"
  private val IT = "HMRC-MTD-IT"
  private val CGT = "HMRC-CGT-PD"
  private val PPT = "HMRC-PPT-ORG"
  private val TERS = "HMRC-TERS-ORG"
  private val TERSNT = "HMRC-TERSNT-ORG"

  "Client count for available tax services" when {
    "agent has all types of clients AND no existing tax groups" should {
      "return map with client count for all services" in new TestScope {
        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(CGT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(PPT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(TERS, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(TERSNT, result = false)

        val expectedCount = Map(
          "HMRC-MTD-VAT"  -> 2,
          "HMRC-CGT-PD"   -> 3,
          "HMRC-PPT-ORG"  -> 4,
          "HMRC-MTD-IT"   -> 5,
          "HMRC-TERS" -> 7 // Combined trusts
        )

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }
    }

    "agent has all types of clients AND some existing tax groups" should {
      "return map with client count for available services" in new TestScope {
        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(CGT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(PPT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(TERS, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(TERSNT, result = false)

        val expectedCount = Map(
          "HMRC-CGT-PD"   -> 3,
          "HMRC-MTD-IT"   -> 5,
          "HMRC-TERS" -> 7 // Combined trusts
        )

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }
    }

    "agent has all types of clients AND all tax groups exist" should {
      "return empty map" in new TestScope {

        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(CGT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(PPT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(TERS, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(TERSNT, result = true)

        val expectedCount = Map.empty[String, Int]

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }
    }

    "agent has only some types of clients" should {
      "return tax service groups for only those types of clients" in new TestScope {
        mockAUCDGetClientCount(Some(Map("HMRC-MTD-VAT" -> 12, "HMRC-MTD-IT" -> 3)))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = false)

        val expectedCount = Map("HMRC-MTD-VAT" -> 12, "HMRC-MTD-IT" -> 3)

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }

      "return tax service groups for only services where a group does not already exist" in new TestScope {
        mockAUCDGetClientCount(Some(Map("HMRC-MTD-VAT" -> 12, "HMRC-MTD-IT" -> 3, "HMRC-TERSNT-ORG" -> 8)))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = false)
        mockTaxGroupsRepositoryGroupExistsForService(TERSNT, result = true)

        val expectedCount = Map("HMRC-MTD-VAT" -> 12, "HMRC-MTD-IT" -> 3)

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }

      "return empty map when all tax services they have clients for already exist" in new TestScope {
        mockAUCDGetClientCount(Some(Map("HMRC-MTD-VAT" -> 12, "HMRC-MTD-IT" -> 3, "HMRC-TERSNT-ORG" -> 8)))
        mockTaxGroupsRepositoryGroupExistsForService(VAT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(IT, result = true)
        mockTaxGroupsRepositoryGroupExistsForService(TERSNT, result = true)

        val expectedCount = Map.empty[String, Int]

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe expectedCount
      }
    }

    "AUCD fails to return full client count" should {
      "return empty map" in new TestScope {
        mockAUCDGetClientCount(None)

        taxGroupsService.clientCountForAvailableTaxServices(arn).futureValue shouldBe Map.empty[String, Int]
      }
    }
  }

  "Client count for existing tax groups" when {
    "no existing tax groups" should {
      "return empty map" in new TestScope {
        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGetAll(Seq.empty)

        taxGroupsService.clientCountForTaxGroups(arn).futureValue shouldBe Map.empty[String, Int]

      }
    }

    "some existing tax groups" should {
      "return map with client count for existing groups" in new TestScope {
        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGetAll(Seq(taxGroup)) // vat group

        val expectedCount = Map("HMRC-MTD-VAT" -> 2)

        taxGroupsService.clientCountForTaxGroups(arn).futureValue shouldBe expectedCount
      }
    }

    "all tax groups exist" should {
      "return map with client count for all services" in new TestScope {
        val allTaxGroups = Seq(
          taxGroup, // vat group
          taxGroup.copy(service = serviceCgt),
          taxGroup.copy(service = servicePpt),
          taxGroup.copy(service = serviceMtdit),
          taxGroup.copy(service = "HMRC-TERS")
        )

        mockAUCDGetClientCount(Some(fullCountMap))
        mockTaxGroupsRepositoryGetAll(allTaxGroups)

        val expectedCount = Map(
          "HMRC-MTD-VAT" -> 2,
          "HMRC-CGT-PD"  -> 3,
          "HMRC-PPT-ORG" -> 4,
          "HMRC-MTD-IT"  -> 5,
          "HMRC-TERS"    -> 7 // combined trusts
        )

        taxGroupsService.clientCountForTaxGroups(arn).futureValue shouldBe expectedCount
      }
    }

    "AUCD fails to return full client count" should {
      "return empty map" in new TestScope {
        mockAUCDGetClientCount(None)
        mockTaxGroupsRepositoryGetAll(Seq.empty) // does not matter

        taxGroupsService.clientCountForTaxGroups(arn).futureValue shouldBe Map.empty[String, Int]
      }
    }

    "Adding team member to a group" when {

      "works as expected when successful " should {
        s"return $TaxServiceGroupUpdated" in new TestScope {
          mockAddTeamMemberToGroup(dbId, user, 1)
          taxGroupsService.addMemberToGroup(dbId, user).futureValue shouldBe TaxServiceGroupUpdated
        }
      }

      "works as expected when no update made due to group not found or something " should {
        s"return $TaxServiceGroupNotUpdated" in new TestScope {
          mockAddTeamMemberToGroup(dbId, user, 0)
          taxGroupsService.addMemberToGroup(dbId, user).futureValue shouldBe TaxServiceGroupNotUpdated
        }
      }
    }
  }

}
