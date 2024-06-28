package uk.gov.hmrc.agentpermissions.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.mvc.Action
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, OptinRepository, TaxGroupsRepositoryV2}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject()(
                                    customGroupsRepositoryV2: CustomGroupsRepositoryV2,
                                    optinRepository: OptinRepository,
                                    taxGroupsRepositoryV2: TaxGroupsRepositoryV2)(implicit ec: ExecutionContext, cc: ControllerComponents)
extends BackendController(cc) with Logging {

  def delete(arn: String): Action[AnyContent] = Action.async { _ =>
    for {
      a <- customGroupsRepositoryV2.delete(arn)
      b <- optinRepository.delete(arn)
      c <- taxGroupsRepositoryV2.delete(arn)
      _ = logger.info(s"")
    } yield Ok
  }


}
