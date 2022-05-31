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
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn}
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, RecordInserted, RecordUpdated}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AccessGroupsServiceImpl])
trait AccessGroupsService {
  def create(accessGroup: AccessGroup)(implicit ec: ExecutionContext): Future[AccessGroupCreationStatus]
  def groupSummaries(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroupSummary]]
  def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]]
}

@Singleton
class AccessGroupsServiceImpl @Inject() (accessGroupsRepository: AccessGroupsRepository)
    extends AccessGroupsService with Logging {

  override def create(accessGroup: AccessGroup)(implicit ec: ExecutionContext): Future[AccessGroupCreationStatus] =
    accessGroupsRepository.get(accessGroup.arn, accessGroup.groupName) flatMap {
      case Some(_) =>
        Future.successful(AccessGroupExists)
      case _ =>
        accessGroupsRepository.upsert(accessGroup) flatMap {
          case None =>
            Future.successful(AccessGroupNotCreated)
          case Some(upsertType) =>
            upsertType match {
              case RecordInserted(creationId) =>
                val groupId = GroupId(accessGroup.arn, accessGroup.groupName).encode
                logger.info(s"Created access group. DB id: '$creationId', gid: '$groupId'")
                Future.successful(AccessGroupCreated(groupId))
              case RecordUpdated =>
                logger.warn("Should not have updated when the request was for a creation")
                Future.successful(AccessGroupNotCreated)
            }
        }
    }

  override def groupSummaries(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[AccessGroupSummary]] =
    accessGroupsRepository.get(arn).map {
      _.map(accessGroup =>
        AccessGroupSummary(
          GroupId(arn, accessGroup.groupName).encode,
          accessGroup.groupName,
          accessGroup.clients.fold(0)(_.size),
          accessGroup.teamMembers.fold(0)(_.size)
        )
      )
    }

  override def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[AccessGroup]] =
    accessGroupsRepository.get(groupId.arn, groupId.groupName)
}

case class AccessGroupSummary(groupId: String, groupName: String, clientCount: Int, teamMemberCount: Int)

object AccessGroupSummary {
  implicit val formatAccessGroupSummary: OFormat[AccessGroupSummary] = Json.format[AccessGroupSummary]
}

sealed trait AccessGroupCreationStatus
case class AccessGroupCreated(creationId: String) extends AccessGroupCreationStatus
case object AccessGroupExists extends AccessGroupCreationStatus
case object AccessGroupNotCreated extends AccessGroupCreationStatus
