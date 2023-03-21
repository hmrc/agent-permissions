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

import org.scalamock.handlers.{CallHandler1, CallHandler3}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.model.BetaInviteRecord
import uk.gov.hmrc.agentpermissions.repository.{BetaInviteRepository, RecordInserted, UpsertType}
import uk.gov.hmrc.agentpermissions.service.audit.AuditService
import uk.gov.hmrc.agents.accessgroups.AgentUser
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class BetaInviteServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val insertedId = "insertedId"

    val mockBetaInviteRepository: BetaInviteRepository = mock[BetaInviteRepository]
    val mockBetaInviteRecordBuilder: BetaInviteRecordBuilder = mock[BetaInviteRecordBuilder]
    val mockAuditService: AuditService = mock[AuditService]

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val betaInviteService =
      new BetaInviteServiceImpl(
        mockBetaInviteRepository,
        mockBetaInviteRecordBuilder
      )

    def mockBetaInviteRepositoryGet(
      maybeBetaInviteRecord: Option[BetaInviteRecord]
    ): CallHandler1[AgentUser, Future[Option[BetaInviteRecord]]] =
      (mockBetaInviteRepository.get(_: AgentUser)).expects(user).returning(Future.successful(maybeBetaInviteRecord))

    def mockBetaInviteRecordBuilderForUpdating(
      maybeExistingBetaInviteRecord: Option[BetaInviteRecord],
      maybeBetaInviteRecordToUpdate: Option[BetaInviteRecord]
    ): CallHandler3[Arn, AgentUser, Option[BetaInviteRecord], Option[BetaInviteRecord]] =
      (mockBetaInviteRecordBuilder
        .forUpdating(_: Arn, _: AgentUser, _: Option[BetaInviteRecord]))
        .expects(arn, user, maybeExistingBetaInviteRecord)
        .returning(maybeBetaInviteRecordToUpdate)

    def mockBetaInviteRepositoryUpsert(
      maybeBetaInviteRecordToUpdate: Option[BetaInviteRecord],
      maybeUpsertType: Option[UpsertType]
    ): CallHandler1[BetaInviteRecord, Future[Option[UpsertType]]] =
      (mockBetaInviteRepository
        .upsert(_: BetaInviteRecord))
        .expects(maybeBetaInviteRecordToUpdate.get)
        .returning(Future.successful(maybeUpsertType))

  }

  s"Calling hideBetaInvite" when {

    "beta invite record does not exist" should {

      "create beta invite record" in new TestScope {

        mockBetaInviteRepositoryGet(None)
        val maybeBetaInviteRecordToUpdate: Option[BetaInviteRecord] =
          Some(BetaInviteRecord(arn, user.id, hideBetaInvite = true))
        mockBetaInviteRecordBuilderForUpdating(None, maybeBetaInviteRecordToUpdate)
        mockBetaInviteRepositoryUpsert(maybeBetaInviteRecordToUpdate, Some(RecordInserted(insertedId)))

        whenReady(betaInviteService.hideBetaInvite(arn, user)) { maybeUpsertResult =>
          maybeUpsertResult.get shouldBe RecordInserted(insertedId)
        }
      }
    }

    "beta invite record exists" should {

      "not update the BetaInvite record" in new TestScope {

        val maybeExistingBetaInviteRecord: Option[BetaInviteRecord] =
          Some(BetaInviteRecord(arn, user.id, hideBetaInvite = true))
        mockBetaInviteRepositoryGet(maybeExistingBetaInviteRecord)
        mockBetaInviteRecordBuilderForUpdating(maybeExistingBetaInviteRecord, None)

        whenReady(betaInviteService.hideBetaInvite(arn, user)) { maybeUpsertResult =>
          maybeUpsertResult shouldBe None
        }
      }
    }
  }

  s"Calling hideBetaInviteCheck" should {

    "return false when BetaInviteRecord does not exist" in new TestScope {
      mockBetaInviteRepositoryGet(None)

      whenReady(betaInviteService.hideBetaInviteCheck(arn, user)) { hideBetaInvite =>
        hideBetaInvite shouldBe false
      }
    }

    "return true when BetaInviteRecord exists" in new TestScope {
      mockBetaInviteRepositoryGet(Some(BetaInviteRecord(arn, user.id, hideBetaInvite = true)))

      whenReady(betaInviteService.hideBetaInviteCheck(arn, user)) { hideBetaInvite =>
        hideBetaInvite shouldBe true
      }
    }

    // Never occurs bc data never set to false, but coverage?
    "return false when BetaInviteRecord exists" in new TestScope {
      mockBetaInviteRepositoryGet(Some(BetaInviteRecord(arn, user.id)))

      whenReady(betaInviteService.hideBetaInviteCheck(arn, user)) { hideBetaInvite =>
        hideBetaInvite shouldBe false
      }
    }

  }

}
