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

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.repository.TaxGroupsRepositoryV2
import uk.gov.hmrc.agents.accessgroups.GroupSummary
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupSummaryServiceImpl])
trait GroupSummaryService {
  def getAllGroupSummaries(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]
  def getAllGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]
  def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

}

@Singleton
class GroupSummaryServiceImpl @Inject() (
  taxGroupsRepo: TaxGroupsRepositoryV2,
  customGroupsService: CustomGroupsService,
  taxGroupsService: TaxGroupsService
) extends GroupSummaryService with Logging {

  override def getAllGroupSummaries(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]] =
    for {
      customGroups <- customGroupsService.getAllCustomGroups(arn)
      customSummaries = customGroups.map(GroupSummary.of(_))
      taxGroups           <- taxGroupsService.getAllTaxServiceGroups(arn)
      taxGroupClientCount <- taxGroupsService.clientCountForTaxGroups(arn)
      taxSummaries = taxGroups.map(group =>
                       GroupSummary
                         .of(group)
                         .copy(clientCount = Option(taxGroupClientCount(group.service)))
                     )
      combinedSorted = (customSummaries ++ taxSummaries).sortBy(_.groupName.toLowerCase())
    } yield combinedSorted

  override def getAllGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForClient(arn, enrolmentKey)
      maybeTaxGroup   <- taxGroupsRepo.getByService(arn, enrolmentKey.split('~').head)
      maybeTaxGroupSummary =
        maybeTaxGroup.fold(Seq.empty[GroupSummary])(group => Seq(GroupSummary.of(group)))
      combinedSummaries = customSummaries ++ maybeTaxGroupSummary
    } yield combinedSummaries

  override def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForTeamMember(arn, userId)
      taxSummaries    <- taxGroupsService.getTaxGroupSummariesForTeamMember(arn, userId)
      combinedSummaries = customSummaries ++ taxSummaries
    } yield combinedSummaries

}
