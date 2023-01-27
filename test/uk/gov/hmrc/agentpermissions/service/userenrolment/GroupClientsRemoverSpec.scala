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

import org.scalamock.handlers.CallHandler4
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class GroupClientsRemoverSpec extends BaseSpec {

  "Removing clients from access group" when {

    "removal enrolments contain some that exist in access group" should {
      "remove only those matching enrolments of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val removalEnrolmentKeys: Set[String] = Set(clientPpt, clientCgt, clientTrust).map(_.enrolmentKey)

        mockAuditServiceAuditAccessGroupClientsRemoval()

        val accessGroupWithClientsRemoved: CustomGroup =
          groupClientsRemover.removeClientsFromGroup(accessGroup, removalEnrolmentKeys, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe Some(Set(clientVat))
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
      }
    }

    "removal enrolments do not contain any that exist in access group" should {
      "not remove any enrolments of access group" in new TestScope {
        val accessGroup: CustomGroup =
          buildAccessGroup(Some(Set(agentUser1)), Some(Set(clientVat, clientPpt, clientCgt)))

        val removalEnrolmentKeys: Set[String] = Set(clientTrust).map(_.enrolmentKey)

        val accessGroupWithClientsRemoved: CustomGroup =
          groupClientsRemover.removeClientsFromGroup(accessGroup, removalEnrolmentKeys, agentUser1)

        accessGroupWithClientsRemoved.clients shouldBe accessGroup.clients
        accessGroupWithClientsRemoved.teamMembers shouldBe accessGroup.teamMembers
      }
    }
  }

  trait TestScope {

    val mockAuditService: AuditService = mock[AuditService]

    val groupClientsRemover = new GroupClientsRemoverImpl(mockAuditService)

    val arn: Arn = Arn("KARN1234567")
    val groupName: String = "groupName"
    val agentUser1: AgentUser = AgentUser("userId1", "userName")
    val now: LocalDateTime = LocalDateTime.now()

    val clientVat: Client = Client(s"$serviceVat~$serviceIdentifierKeyVat~101747641", "John Innes")

    val clientPpt: Client = Client(s"$servicePpt~$serviceIdentifierKeyPpt~XAPPT0000012345", "Frank Wright")

    val clientCgt: Client = Client(s"$serviceCgt~$serviceIdentifierKeyCgt~XMCGTP123456789", "George Candy")

    val clientTrust: Client = Client(s"$serviceTrust~$serviceIdentifierKeyTrust~0123456789", "Trust Client")

    implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    def buildAccessGroup(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]]): CustomGroup =
      CustomGroup(
        arn,
        groupName,
        now,
        now,
        agentUser1,
        agentUser1,
        teamMembers,
        clients
      )

    def mockAuditServiceAuditAccessGroupClientsRemoval()
      : CallHandler4[CustomGroup, Set[Client], HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditService
        .auditAccessGroupClientsRemoval(_: CustomGroup, _: Set[Client])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

  }

}
