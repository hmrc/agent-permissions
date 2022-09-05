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

package uk.gov.hmrc.agentpermissions.config

import com.google.inject.ImplementedBy
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {
  def agentUserClientDetailsBaseUrl: String
  def agentSizeMaxClientCountAllowed: Int
  def checkArnAllowList: Boolean
  def allowedArns: Seq[String]
  def clientsRemovalChunkSize: Int
  def teamMembersRemovalChunkSize: Int
  def accessGroupChunkSize: Int
  def useEnrolmentAssignmentsChunkSize: Int
}

@Singleton
class AppConfigImpl @Inject() (servicesConfig: ServicesConfig, configuration: Configuration) extends AppConfig {
  override lazy val agentUserClientDetailsBaseUrl: String = servicesConfig.baseUrl("agent-user-client-details")
  override lazy val agentSizeMaxClientCountAllowed: Int = servicesConfig.getInt("agentsize.maxClientCountAllowed")
  override lazy val checkArnAllowList: Boolean = servicesConfig.getBoolean("features.check-arn-allow-list")
  override lazy val allowedArns: Seq[String] = configuration.get[Seq[String]]("allowed.arns")
  override lazy val clientsRemovalChunkSize: Int = servicesConfig.getInt("audit.clients-removal-chunk-size")
  override lazy val teamMembersRemovalChunkSize: Int = servicesConfig.getInt("audit.team-members-removal-chunk-size")
  override lazy val accessGroupChunkSize: Int = servicesConfig.getInt("audit.access-group-chunk-size")
  override lazy val useEnrolmentAssignmentsChunkSize: Int =
    servicesConfig.getInt("audit.user-enrolment-assignments-chunk-size")
}
