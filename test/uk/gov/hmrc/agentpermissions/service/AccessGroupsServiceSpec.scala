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

import org.scalamock.handlers.{CallHandler1, CallHandler2}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.repository.{AccessGroupsRepository, RecordInserted, RecordUpdated, UpsertType}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AccessGroupsServiceSpec extends BaseSpec {

  trait TestScope {
    val arn: Arn = Arn("KARN1234567")
    val user: AgentUser = AgentUser("userId", "userName")
    val groupName = "some group"
    val insertedId = "insertedId"
    val accessGroup: AccessGroup = AccessGroup(arn, groupName, now, now, user, user, None, None)

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val mockAccessGroupsRepository: AccessGroupsRepository = mock[AccessGroupsRepository]

    val accessGroupsService: AccessGroupsService = new AccessGroupsServiceImpl(mockAccessGroupsRepository)

    lazy val now: LocalDateTime = LocalDateTime.now()

    def mockAccessGroupsRepositoryGet(
      maybeAccessGroup: Option[AccessGroup]
    ): CallHandler2[Arn, String, Future[Option[AccessGroup]]] =
      (mockAccessGroupsRepository
        .get(_: Arn, _: String))
        .expects(arn, groupName)
        .returning(Future.successful(maybeAccessGroup))

    def mockAccessGroupsRepositoryUpsert(
      maybeUpsertType: Option[UpsertType]
    ): CallHandler1[AccessGroup, Future[Option[UpsertType]]] = (mockAccessGroupsRepository
      .upsert(_: AccessGroup))
      .expects(accessGroup)
      .returning(Future.successful(maybeUpsertType))
  }

  "Calling create" when {

    "group of that name already exists" should {
      s"return $AccessGroupExists" in new TestScope {
        mockAccessGroupsRepositoryGet(Some(accessGroup))

        accessGroupsService
          .create(accessGroup)
          .futureValue shouldBe AccessGroupExists
      }
    }

    "group of that name does not already exist" when {

      "upsert calls returns nothing" should {
        s"return $AccessGroupNotCreated" in new TestScope {
          mockAccessGroupsRepositoryGet(None)
          mockAccessGroupsRepositoryUpsert(None)

          accessGroupsService
            .create(accessGroup)
            .futureValue shouldBe AccessGroupNotCreated
        }
      }

      "upsert calls returns some value" when {

        s"upsert calls returns $RecordInserted" should {
          s"return $AccessGroupCreated" in new TestScope {
            mockAccessGroupsRepositoryGet(None)
            mockAccessGroupsRepositoryUpsert(Some(RecordInserted(insertedId)))

            accessGroupsService
              .create(accessGroup)
              .futureValue shouldBe AccessGroupCreated(insertedId)
          }
        }

        s"upsert calls returns $RecordUpdated" should {
          s"return $AccessGroupNotCreated" in new TestScope {
            mockAccessGroupsRepositoryGet(None)
            mockAccessGroupsRepositoryUpsert(Some(RecordUpdated))

            accessGroupsService
              .create(accessGroup)
              .futureValue shouldBe AccessGroupNotCreated
          }
        }
      }
    }
  }

}
