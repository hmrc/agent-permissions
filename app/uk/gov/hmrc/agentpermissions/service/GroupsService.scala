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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, EnrolmentKey}
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agentpermissions.repository.{CustomGroupsRepositoryV2, TaxGroupsRepositoryV2}
import uk.gov.hmrc.agents.accessgroups.{Client, CustomGroup, GroupSummary}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupsServiceImpl])
trait GroupsService {
  def getAllGroupSummaries(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]
  def getAllGroupSummariesForClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]
  def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]]

  def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList]

  def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

  def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]]

}

@Singleton
class GroupsServiceImpl @Inject() (
  taxGroupsRepo: TaxGroupsRepositoryV2,
  customGroupsRepository: CustomGroupsRepositoryV2,
  userClientDetailsConnector: UserClientDetailsConnector,
  customGroupsService: CustomGroupsService,
  taxGroupsService: TaxGroupsService
) extends GroupsService with Logging {

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
  ): Future[Seq[GroupSummary]] = {
    val service = if (enrolmentKey.contains("HMRC-TERS")) { "HMRC-TERS" }
    else if (enrolmentKey.contains("HMRC-CBC")) { "HMRC-CBC" }
    else enrolmentKey.split('~').head
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForClient(arn, enrolmentKey)
      maybeTaxGroup   <- taxGroupsRepo.getByService(arn, service)
      maybeTaxGroupSummary =
        maybeTaxGroup.fold(Seq.empty[GroupSummary])(group =>
          if (group.excludedClients.exists(client => client.enrolmentKey == enrolmentKey)) {
            Seq.empty[GroupSummary]
          } else Seq(GroupSummary.of(group))
        )
      combinedSummaries = customSummaries ++ maybeTaxGroupSummary
    } yield combinedSummaries
  }

  override def getAllGroupSummariesForTeamMember(arn: Arn, userId: String)(implicit
    ec: ExecutionContext
  ): Future[Seq[GroupSummary]] =
    for {
      customSummaries <- customGroupsService.getCustomGroupSummariesForTeamMember(arn, userId)
      taxSummaries    <- taxGroupsService.getTaxGroupSummariesForTeamMember(arn, userId)
      combinedSummaries = customSummaries ++ taxSummaries
    } yield combinedSummaries

  override def getAllClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ClientList] =
    for {
      clients      <- userClientDetailsConnector.getClients(arn).map(_.toSet.flatten)
      accessGroups <- if (clients.nonEmpty) customGroupsRepository.get(arn) else Future.successful(Seq.empty)
      enrolmentKeysInCustomGroups = accessGroups.toSet[CustomGroup].flatMap(_.clients).map(_.enrolmentKey)
      taxServiceGroups <- taxGroupsService.getAllTaxServiceGroups(arn)
    } yield clients.foldLeft(ClientList(Set.empty, Set.empty)) { (clientList, client) =>
      val serviceKey = EnrolmentKey.serviceOf(client.enrolmentKey) match {
        // both types of trusts and cbc client are represented by a single truncated key in tax service groups
        case "HMRC-TERS-ORG" | "HMRC-TERSNT-ORG"   => "HMRC-TERS"
        case "HMRC-CBC-ORG" | "HMRC-CBC-NONUK-ORG" => "HMRC-CBC"
        case sk                                    => sk
      }
      // The client is considered 'assigned' if: ...
      if (
        enrolmentKeysInCustomGroups.contains(client.enrolmentKey) || // ... they are in a custom access group, OR ...
        taxServiceGroups.exists(tsg => // ... there is a tax service group AND they are not excluded from it.
          tsg.service == serviceKey &&
            !tsg.excludedClients.exists(_.enrolmentKey == client.enrolmentKey)
        )
      ) {
        clientList.copy(assigned = clientList.assigned + client)
      } else {
        clientList.copy(unassigned = clientList.unassigned + client)
      }
    }

  override def getAssignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.assigned)

  override def getUnassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Set[Client]] =
    getAllClients(arn).map(_.unassigned)

}
