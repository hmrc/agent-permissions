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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.repository.*
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class KeyRotationService @Inject (
  lockRepository: LockRepository,
  appConfig: AppConfig,
  optinRepository: OptinRepository,
  customGroupsRepositoryV2: CustomGroupsRepositoryV2,
  taxGroupsRepositoryV2: TaxGroupsRepositoryV2
)(using ExecutionContext)
    extends Logging:

  private val lockService = LockService(
    lockRepository,
    lockId = "key-rotation-lock",
    ttl = appConfig.keyRotation.maxTotalDuration
  )

  val migration: Future[Option[Unit]] =
    if !appConfig.keyRotation.enabled then Future.successful(None)
    else
      val batchSize = appConfig.keyRotation.batchSize
      val deadline = appConfig.keyRotation.maxTotalDuration.fromNow

      logger.info("Starting key rotation migration")

      lockService.withLock:
        for
          _ <- optinRepository.migrate(batchSize, deadline)
          _ <- customGroupsRepositoryV2.migrate(batchSize, deadline)
          _ <- taxGroupsRepositoryV2.migrate(batchSize, deadline)
        yield ()

  migration.onComplete:
    case Failure(error)   => logger.error("Key rotation migration failed", error)
    case Success(Some(_)) => logger.info("Key rotation migration completed successfully")
    case Success(None)    => logger.warn("Key rotation migration disabled or did not acquire lock, skipping")
