/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, OptinRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject() (
  customGroupsRepositoryV2: CustomGroupsRepositoryV2,
  optinRepository: OptinRepository
)(implicit ec: ExecutionContext, cc: ControllerComponents)
    extends BackendController(cc) with Logging {

  def delete(arn: String): Action[AnyContent] = Action.async { _ =>
    for {
      a <- customGroupsRepositoryV2.delete(arn)
      b <- optinRepository.delete(arn)
      _ = logger.info(s"deleted $a perf test users from custom groups and $b users from opt-in to access groups")
    } yield Ok
  }

}
