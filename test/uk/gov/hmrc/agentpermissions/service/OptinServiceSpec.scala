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
import play.api.http.Status.OK
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{OptinRepository, RecordInserted, RecordUpdated, UpsertType}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class OptinServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val insertedId = "insertedId"

    val mockOptinRepository: OptinRepository = mock[OptinRepository]
    val mockOptinRecordBuilder: OptinRecordBuilder = mock[OptinRecordBuilder]
    val mockOptedInStatusHandler: OptedInStatusHandler = mock[OptedInStatusHandler]
    val mockNotOptedInStatusHandler: NotOptedInStatusHandler = mock[NotOptedInStatusHandler]
    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val optinService =
      new OptinServiceImpl(
        mockOptinRepository,
        mockOptinRecordBuilder,
        mockOptedInStatusHandler,
        mockNotOptedInStatusHandler,
        mockUserClientDetailsConnector,
        mockAuditConnector
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

    def mockAuditConnectorSendExplicitAudit(): CallHandler4[String, JsObject, HeaderCarrier, ExecutionContext, Unit] =
      (mockAuditConnector
        .sendExplicitAudit(_: String, _: JsObject)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returning(())

    def mockUserClientDetailsConnectorGetClientListStatus(
      maybeClientListStatus: Option[Int]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Int]]] =
      (mockUserClientDetailsConnector
        .getClientListStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future successful maybeClientListStatus)
  }

  s"Calling optin" when {

    "optin record does not exist" should {

      "create optin record" in new TestScope {

        mockOptinRepositoryGet(None)
        val maybeOptinRecordToUpdate: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        mockOptinRecordBuilderForUpdating(None, maybeOptinRecordToUpdate, OptedIn)
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordInserted(insertedId)))
        mockAuditConnectorSendExplicitAudit()
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optin(arn, user)) { maybeOptinRequestStatus =>
          maybeOptinRequestStatus.get shouldBe OptinCreated
        }
      }
    }

    "optin record exists whose latest event if of matching event type" should {

      "not update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now()))))
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, None, OptedIn)
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optin(arn, user)) { maybeOptinRequestStatus =>
          maybeOptinRequestStatus shouldBe None
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
        mockAuditConnectorSendExplicitAudit()
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optin(arn, user)) { maybeOptinRequestStatus =>
          maybeOptinRequestStatus.get shouldBe OptinUpdated
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
        mockOptinRepositoryUpsert(maybeOptinRecordToUpdate, Some(RecordInserted(insertedId)))
        mockAuditConnectorSendExplicitAudit()
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optout(arn, user)) { maybeOptoutRequestStatus =>
          maybeOptoutRequestStatus.get shouldBe OptoutCreated
        }
      }
    }

    "optin record exists whose latest event if of matching event type" should {

      "not update the optin record" in new TestScope {

        val maybeExistingOptinRecord: Option[OptinRecord] =
          Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now()))))
        mockOptinRepositoryGet(maybeExistingOptinRecord)
        mockOptinRecordBuilderForUpdating(maybeExistingOptinRecord, None, OptedOut)
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optout(arn, user)) { maybeOptoutRequestStatus =>
          maybeOptoutRequestStatus shouldBe None
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
        mockAuditConnectorSendExplicitAudit()
        mockUserClientDetailsConnectorGetClientListStatus(Some(OK))

        whenReady(optinService.optout(arn, user)) { maybeOptoutRequestStatus =>
          maybeOptoutRequestStatus.get shouldBe OptoutUpdated
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

  s"Calling optin record exists" when {

    "optin record does not exist" should {

      s"return false" in new TestScope {
        mockOptinRepositoryGet(None)

        optinService.optinRecordExists(arn).futureValue shouldBe false
      }
    }

    "optin record exists" when {

      s"optin record status is $OptedIn" should {

        s"return true" in new TestScope {
          mockOptinRepositoryGet(Some(OptinRecord(arn, List(OptinEvent(OptedIn, user, LocalDateTime.now())))))

          optinService.optinRecordExists(arn).futureValue shouldBe true
        }
      }

      s"optin record status is $OptedOut" should {

        s"return false" in new TestScope {
          mockOptinRepositoryGet(Some(OptinRecord(arn, List(OptinEvent(OptedOut, user, LocalDateTime.now())))))

          optinService.optinRecordExists(arn).futureValue shouldBe false
        }
      }
    }

  }

}
