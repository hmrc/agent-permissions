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

import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentpermissions.repository.TaxServiceGroupsRepository

import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupsServiceImpl])
trait GroupsService {
  def getAllGroupSummaries(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AccessGroupSummary]]

  def getAllGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]]

  def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]]

//  def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList]
//
//  def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]
//
//  def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

}

@Singleton
class GroupsServiceImpl @Inject() (
  taxServiceGroupsRepo: TaxServiceGroupsRepository,
  customGroupsService: AccessGroupsService,
  taxServiceGroupsService: TaxServiceGroupsService
) extends GroupsService with Logging {

  override def getAllGroupSummaries(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AccessGroupSummary]] =
    for {
      customGroups <- customGroupsService.getAllCustomGroups(arn)
      customSummaries = customGroups.map(AccessGroupSummary.convertCustomGroup)
      taxGroups <- taxServiceGroupsService.getAllTaxServiceGroups(arn)
      taxSummaries = taxGroups.map(AccessGroupSummary.convertTaxServiceGroup)
      combinedSorted = (customSummaries ++ taxSummaries).sortBy(_.groupName.toLowerCase())
    } yield combinedSorted

  override def getAllGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]] =
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForClient(arn, enrolmentKey)
      maybeTaxGroup   <- taxServiceGroupsRepo.getByService(arn, enrolmentKey.split('~').head)
      maybeTaxGroupSummary =
        maybeTaxGroup.fold(Seq.empty[AccessGroupSummary])(group =>
          Seq(AccessGroupSummary.convertTaxServiceGroup(group))
        )
      combinedSummaries = customSummaries ++ maybeTaxGroupSummary
    } yield combinedSummaries

  override def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[AccessGroupSummary]] =
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForTeamMember(arn, userId)
      taxSummaries    <- taxServiceGroupsService.getTaxGroupSummariesForTeamMember(arn, userId)
      combinedSummaries = customSummaries ++ taxSummaries
    } yield combinedSummaries

}
