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

package uk.gov.hmrc.agentpermissions.service

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, RecordInserted, RecordUpdated}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class OptinServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")

    val optinRepository: OptinRepository = mock[OptinRepository]
    val optinRecordBuilder: OptinRecordBuilder = mock[OptinRecordBuilder]

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val optinService = new OptinService(optinRepository, optinRecordBuilder)
  }

  s"Calling optin" when {

    "optin record does not exist" should {

      "insert optin record" in new TestScope {

        when(optinRepository.get(arn)).thenReturn(Future.successful(None))
        val maybeOptinRecordToUpdate: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        when(optinRecordBuilder.forUpdating(arn, user, None, OptedIn))
          .thenReturn(maybeOptinRecordToUpdate)
        when(optinRepository.upsert(maybeOptinRecordToUpdate.get)).thenReturn(Future.successful(Some(RecordInserted)))

        whenReady(optinService.optin(arn, user)) { maybeUpsertType =>
          val upsertType = maybeUpsertType.get
          upsertType shouldBe RecordInserted
        }
      }
    }

    "optin record exists whose latest event if of matching event type" should {

      "not update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        when(optinRepository.get(arn))
          .thenReturn(Future.successful(maybeExistingOptinRecord))
        when(optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, OptedIn))
          .thenReturn(None)

        whenReady(optinService.optin(arn, user)) { maybeUpsertType =>
          maybeUpsertType shouldBe None
        }
      }
    }

    "optin record exists whose latest event is of different event type" should {

      "update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        when(optinRepository.get(arn))
          .thenReturn(Future.successful(maybeExistingOptinRecord))
        val maybeOptinRecordToUpdate: Option[OptinRecord] = maybeExistingOptinRecord.map(record =>
          record.copy(history = record.history :+ OptinEvent(OptedIn, user, LocalDateTime.now()))
        )
        when(optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, OptedIn))
          .thenReturn(maybeOptinRecordToUpdate)
        when(optinRepository.upsert(maybeOptinRecordToUpdate.get)).thenReturn(Future.successful(Some(RecordUpdated)))

        whenReady(optinService.optin(arn, user)) { maybeUpsertType =>
          val upsertType = maybeUpsertType.get
          upsertType shouldBe RecordUpdated
        }
      }
    }
  }

  s"Calling optout" when {

    "optin record does not exist" should {

      "insert optin record" in new TestScope {

        when(optinRepository.get(arn)).thenReturn(Future.successful(None))
        val maybeOptinRecordToUpdate: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        when(optinRecordBuilder.forUpdating(arn, user, None, OptedOut))
          .thenReturn(maybeOptinRecordToUpdate)
        when(optinRepository.upsert(maybeOptinRecordToUpdate.get)).thenReturn(Future.successful(Some(RecordInserted)))

        whenReady(optinService.optout(arn, user)) { maybeUpsertType =>
          val upsertType = maybeUpsertType.get
          upsertType shouldBe RecordInserted
        }
      }
    }

    "optin record exists whose latest event if of matching event type" should {

      "not update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        when(optinRepository.get(arn))
          .thenReturn(Future.successful(maybeExistingOptinRecord))
        when(optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, OptedOut))
          .thenReturn(None)

        whenReady(optinService.optout(arn, user)) { maybeUpsertType =>
          maybeUpsertType shouldBe None
        }
      }
    }

    "optin record exists whose latest event is of different event type" should {

      "update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        when(optinRepository.get(arn))
          .thenReturn(Future.successful(maybeExistingOptinRecord))
        val maybeOptinRecordToUpdate: Option[OptinRecord] = maybeExistingOptinRecord.map(record =>
          record.copy(history = record.history :+ OptinEvent(OptedOut, user, LocalDateTime.now()))
        )
        when(optinRecordBuilder.forUpdating(arn, user, maybeExistingOptinRecord, OptedOut))
          .thenReturn(maybeOptinRecordToUpdate)
        when(optinRepository.upsert(maybeOptinRecordToUpdate.get)).thenReturn(Future.successful(Some(RecordUpdated)))

        whenReady(optinService.optout(arn, user)) { maybeUpsertType =>
          val upsertType = maybeUpsertType.get
          upsertType shouldBe RecordUpdated
        }
      }
    }
  }

}
