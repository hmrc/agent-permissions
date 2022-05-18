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

import org.scalamock.handlers.{CallHandler1, CallHandler3, CallHandler4}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, RecordInserted, RecordUpdated, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class OptinServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")

    val mockOptinRepository: OptinRepository = mock[OptinRepository]
    val mockOptinRecordBuilder: OptinRecordBuilder = mock[OptinRecordBuilder]
    val mockOptedInStatusHandler: OptedInStatusHandler = mock[OptedInStatusHandler]
    val mockNotOptedInStatusHandler: NotOptedInStatusHandler = mock[NotOptedInStatusHandler]

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val optinService =
      new OptinService(
        mockOptinRepository,
        mockOptinRecordBuilder,
        mockOptedInStatusHandler,
        mockNotOptedInStatusHandler
      )

    def mockOptinRepositoryGet(maybeOptinRecord: Option[OptinRecord]): CallHandler1[Arn, Future[Option[OptinRecord]]] =
      (mockOptinRepository.get(_: Arn)).expects(arn).returning(Future.successful(maybeOptinRecord))

    def mockOptinRecordBuilderForUpdating(
      maybeExistingOptinRecord: Option[OptinRecord],
      maybeOptinRecordToUpdate: Option[OptinRecord],
      optinEventType: OptinEventType
    ): CallHandler4[Arn, AgentUser, Option[OptinRecord], OptinEventType, Option[OptinRecord]] =
      (mockOptinRecordBuilder
        .forUpdating(_: Arn, _: AgentUser, _: Option[OptinRecord], _: OptinEventType))
        .expects(arn, user, maybeExistingOptinRecord, optinEventType)
        .returning(maybeOptinRecordToUpdate)

    def mockOptinRepositoryUpsert(
      maybeOptinRecordToUpdate: Option[OptinRecord],
      maybeUpsertType: Option[UpsertType]
    ): CallHandler1[OptinRecord, Future[Option[UpsertType]]] =
      (mockOptinRepository
        .upsert(_: OptinRecord))
        .expects(maybeOptinRecordToUpdate.get)
        .returning(Future.successful(maybeUpsertType))

    def mockOptedInStatusHandlerIdentifyStatus(
      maybeOptinStatus: Option[OptinStatus]
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[OptinStatus]]] =
      (mockOptedInStatusHandler
        .identifyStatus(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, executionContext, headerCarrier)
        .returning(Future.successful(maybeOptinStatus))

    def mockNotOptedInStatusHandlerIdentifyStatus(
      maybeOptinStatus: Option[OptinStatus]
    ): CallHandler3[Arn, ExecutionContext, HeaderCarrier, Future[Option[OptinStatus]]] =
      (mockNotOptedInStatusHandler
        .identifyStatus(_: Arn)(_: ExecutionContext, _: HeaderCarrier))
        .expects(arn, executionContext, headerCarrier)
        .returning(Future.successful(maybeOptinStatus))
  }

  s"Calling optin" when {

    "optin record does not exist" should {

      "insert optin record" in new TestScope {

        mockOptinRepositoryGet(None)
        val maybeOptinRecordToUpdate: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        mockOptinRecordBuilderForUpdating(None, maybeOptinRecordToUpdate, OptedIn)
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordInserted))

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
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, None, OptedIn)

        whenReady(optinService.optin(arn, user)) { maybeUpsertType =>
          maybeUpsertType shouldBe None
        }
      }
    }

    "optin record exists whose latest event is of different event type" should {

      "update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        val maybeOptinRecordToUpdate: Option[OptinRecord] = maybeExistingOptinRecord.map(record =>
          record.copy(history = record.history :+ OptinEvent(OptedIn, user, LocalDateTime.now()))
        )
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, maybeOptinRecordToUpdate, OptedIn)
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordUpdated))

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

        mockOptinRepositoryGet(None)
        val maybeOptinRecordToUpdate: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        mockOptinRecordBuilderForUpdating(None, maybeOptinRecordToUpdate, OptedOut)
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordInserted))

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
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, None, OptedOut)

        whenReady(optinService.optout(arn, user)) { maybeUpsertType =>
          maybeUpsertType shouldBe None
        }
      }
    }

    "optin record exists whose latest event is of different event type" should {

      "update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        val maybeOptinRecordToUpdate: Option[OptinRecord] = maybeExistingOptinRecord.map(record =>
          record.copy(history = record.history :+ OptinEvent(OptedOut, user, LocalDateTime.now()))
        )
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, maybeOptinRecordToUpdate, OptedOut)
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordUpdated))

        whenReady(optinService.optout(arn, user)) { maybeUpsertType =>
          val upsertType = maybeUpsertType.get
          upsertType shouldBe RecordUpdated
        }
      }
    }
  }

  s"Calling optin status" when {

    "optin record does not exist" should {

      s"delegate to NotOptedInStatusHandler" in new TestScope {
        mockOptinRepositoryGet(None)
        mockNotOptedInStatusHandlerIdentifyStatus(None)

        optinService.optinStatus(arn).futureValue shouldBe None
      }
    }

    "optin record exists" when {

      s"optin record status is $OptedIn" should {

        s"delegate to OptedInStatusHandler" in new TestScope {
          val optinEventType: OptinEventType = OptedIn
          mockOptinRepositoryGet(Some(OptinRecord(arn, List(OptinEvent(optinEventType, user, LocalDateTime.now())))))
          mockOptedInStatusHandlerIdentifyStatus(None)

          optinService.optinStatus(arn).futureValue shouldBe None
        }
      }

      s"optin record status is $OptedOut" should {

        s"delegate to NotOptedInStatusHandler" in new TestScope {
          val optinEventType: OptinEventType = OptedOut
          mockOptinRepositoryGet(Some(OptinRecord(arn, List(OptinEvent(optinEventType, user, LocalDateTime.now())))))
          mockNotOptedInStatusHandlerIdentifyStatus(None)

          optinService.optinStatus(arn).futureValue shouldBe None
        }
      }
    }

  }

}
