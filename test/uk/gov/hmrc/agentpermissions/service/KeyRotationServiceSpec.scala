/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalamock.function.StubFunction3
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.config.{AppConfig, KeyRotationConfig}
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, OptinRepository, TaxGroupsRepositoryV2}
import uk.gov.hmrc.mongo.lock.{Lock, LockRepository}

import java.time.Instant
import scala.concurrent.duration.{Deadline, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class KeyRotationServiceSpec extends TestConstants:

  trait TestScope:
    given ExecutionContext = ExecutionContext.Implicits.global

    val lockRepository: LockRepository = mock[LockRepository]

    val appConfig: AppConfig = mock[AppConfig]

    val optinRepository: OptinRepository = stub[OptinRepository]
    val customGroupsRepository: CustomGroupsRepositoryV2 = stub[CustomGroupsRepositoryV2]
    val taxGroupsRepository: TaxGroupsRepositoryV2 = stub[TaxGroupsRepositoryV2]

    val keyRotationConfig: KeyRotationConfig = KeyRotationConfig(
      enabled = true,
      maxTotalDuration = 10.seconds,
      batchSize = 1
    )

    lazy val keyRotationService = new KeyRotationService(
      lockRepository,
      appConfig,
      optinRepository,
      customGroupsRepository,
      taxGroupsRepository
    )

  "KeyRotationService" when:

    "key rotation is disabled" should:
      "skip migration and complete without running repository migrations" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig.copy(enabled = false)).anyNumberOfTimes()
        lockRepository.takeLock.expects(*, *, *).never()
        lockRepository.releaseLock.expects(*, *).never()

        noException should be thrownBy:
          keyRotationService.migration.futureValue

        (optinRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()
        (customGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()
        (taxGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()

    "key rotation is enabled" should:
      "run migrations for optin, custom groups, and tax groups when lock is acquired" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig).anyNumberOfTimes()
        lockRepository.takeLock
          .expects(*, *, *)
          .returning(Future.successful(Some(Lock("", "", Instant.now(), Instant.MAX))))
        lockRepository.releaseLock
          .expects(*, *)
          .returning(Future.unit)
        (optinRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (customGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (taxGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))

        keyRotationService.migration.futureValue

        (optinRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()
        (customGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()
        (taxGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()

      "skip migration when lock is not acquired" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig).anyNumberOfTimes()
        lockRepository.takeLock.expects(*, *, *).returning(Future.successful(None))
        lockRepository.releaseLock.expects(*, *).never()

        noException should be thrownBy:
          keyRotationService.migration.futureValue

        (optinRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()
        (customGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()
        (taxGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()

      "stop and fail when optin migration fails" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig).anyNumberOfTimes()
        lockRepository.takeLock
          .expects(*, *, *)
          .returning(Future.successful(Some(Lock("", "", Instant.now(), Instant.MAX))))
        lockRepository.releaseLock.expects(*, *).returning(Future.unit)
        (optinRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.failed(RuntimeException("optin migration failed")))
        (customGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (taxGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))

        keyRotationService.migration.failed.futureValue

        (customGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()
        (taxGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()

      "stop and fail when custom groups migration fails" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig).anyNumberOfTimes()
        lockRepository.takeLock
          .expects(*, *, *)
          .returning(Future.successful(Some(Lock("", "", Instant.now(), Instant.MAX))))
        lockRepository.releaseLock.expects(*, *).returning(Future.unit)
        (optinRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (customGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.failed(RuntimeException("custom groups migration failed")))
        (taxGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))

        keyRotationService.migration.failed.futureValue

        (optinRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()
        (taxGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).never()

      "stop and fail when tax groups migration fails" in new TestScope:
        (() => appConfig.keyRotation).expects().returning(keyRotationConfig).anyNumberOfTimes()
        lockRepository.takeLock
          .expects(*, *, *)
          .returning(Future.successful(Some(Lock("", "", Instant.now(), Instant.MAX))))
        lockRepository.releaseLock.expects(*, *).returning(Future.unit)
        (optinRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (customGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.successful(100))
        (taxGroupsRepository
          .migrate(_: Int, _: Deadline)(using _: RequestHeader))
          .when(*, *, *)
          .returns(Future.failed(RuntimeException("tax groups migration failed")))

        keyRotationService.migration.failed.futureValue

        (optinRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()
        (customGroupsRepository.migrate(_: Int, _: Deadline)(using _: RequestHeader)).verify(*, *, *).once()
