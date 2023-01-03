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

import akka.actor.ActorSystem
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.OptedIn
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import java.time.Duration.ofSeconds
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EacdSynchronizer @Inject() (
  optinService: OptinService,
  accessGroupsService: AccessGroupsService,
  mongoLockRepository: MongoLockRepository,
  actorSystem: ActorSystem,
  appConfig: AppConfig
)(implicit executionContext: ExecutionContext)
    extends Logging {

  private lazy val lockService =
    LockService(mongoLockRepository, lockId = "eacd-sync-lock", ttl = appConfig.eacdSyncIntervalSeconds.seconds)

  actorSystem.scheduler.scheduleWithFixedDelay(
    ofSeconds(1),
    ofSeconds(appConfig.eacdSyncIntervalSeconds),
    () =>
      lockService
        .withLock {
          logger.info(s"Starting EACD sync process in the background")

          syncOptedInAgents recover { case ex: Exception =>
            logger.error(s"Problem in EACD sync process: ${ex.getMessage}")
            Seq.empty[AccessGroupUpdateStatus]
          }
        }
        .map {
          case Some(_) => logger.info(s"Finished with EACD sync process. Lock has been released.")
          case None    => logger.warn("Failed to obtain lock for EACD sync process")
        },
    executionContext
  )

  private def syncOptedInAgents: Future[Seq[AccessGroupUpdateStatus]] = {

    // TODO Find a way for backend endpoints to accept requests other than from agent users i.e. one service to another
    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("Bearer XYZ")))

    for {
      optinRecords <- optinService.getAll
      optedInRecords = optinRecords.filter(_.status == OptedIn)
      accessGroupUpdateStatuses <-
        Future
          .sequence(
            optedInRecords.map { optinRecord =>
              logger.info(s"Calling EACD sync for '${optinRecord.arn.value}'")
              accessGroupsService.syncWithEacd(optinRecord.arn, optinRecord.history.head.user)
            }
          )
          .map(_.flatten)
    } yield accessGroupUpdateStatuses

  }
}
