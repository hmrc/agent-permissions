package uk.gov.hmrc.agentpermissions.model

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, Client}

import java.time.LocalDateTime

case class CreateAccessGroupRequest(
                                     groupName: String,
                                     teamMembers: Option[Set[AgentUser]],
                                     clients: Option[Set[Client]]
                                   ) {
  def buildAccessGroup(
                        arn: Arn,
                        agentUser: AgentUser
                      ): AccessGroup = {
    val now = LocalDateTime.now()

    AccessGroup(
      arn,
      Option(groupName).map(_.trim).getOrElse(""),
      now,
      now,
      agentUser,
      agentUser,
      teamMembers,
      clients
    )
  }
}

object CreateAccessGroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[CreateAccessGroupRequest] = Json.format[CreateAccessGroupRequest]
}

case class UpdateAccessGroupRequest(
                                     groupName: Option[String],
                                     teamMembers: Option[Set[AgentUser]],
                                     clients: Option[Set[Client]]
                                   ) {

  def merge(existingAccessGroup: AccessGroup): AccessGroup = {
    val withMergedGroupName = groupName.fold(existingAccessGroup)(name =>
      existingAccessGroup.copy(groupName = Option(name).map(_.trim).getOrElse(""))
    )
    val withMergedClients = clients.fold(withMergedGroupName)(cls => withMergedGroupName.copy(clients = Some(cls)))
    teamMembers.fold(withMergedClients)(members => withMergedClients.copy(teamMembers = Some(members)))
  }
}

object UpdateAccessGroupRequest {
  implicit val format: OFormat[UpdateAccessGroupRequest] = Json.format[UpdateAccessGroupRequest]
}

case class AddMembersToAccessGroupRequest(teamMembers: Option[Set[AgentUser]], clients: Option[Set[Client]])

object AddMembersToAccessGroupRequest {
  implicit val format: OFormat[AddMembersToAccessGroupRequest] = Json.format[AddMembersToAccessGroupRequest]
}
