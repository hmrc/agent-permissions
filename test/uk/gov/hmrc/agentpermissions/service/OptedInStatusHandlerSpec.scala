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

import org.scalamock.handlers.CallHandler3
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class OptedInStatusHandlerSpec extends BaseSpec {

  trait TestScope {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    val optedInStatusHandler = new OptedInStatusHandlerImpl(mockUserClientDetailsConnector)

    def mockUserClientDetailsConnectorCheckGroupAssignments(
      maybeSingleUser: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .isSingleUserAgency(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future.successful(maybeSingleUser))

    def mockUserClientDetailsConnectorOutstandingWorkItemsExist(
      maybeWorkItemsExist: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .outstandingWorkItemsExist(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future.successful(maybeWorkItemsExist))
  }

  "identifyStatus" when {

    "UserClientDetailsConnector returns nothing when checking if single user agency" should {
      "return nothing" in new TestScope {
        mockUserClientDetailsConnectorCheckGroupAssignments(None)

        optedInStatusHandler.identifyStatus(arn).futureValue shouldBe None
      }
    }

    "UserClientDetailsConnector returns some value when checking if single user agency" when {

      "UserClientDetailsConnector returns true" should {
        s"return $OptedInSingleUser" in new TestScope {
          mockUserClientDetailsConnectorCheckGroupAssignments(Some(true))

          optedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedInSingleUser)
        }
      }

      "UserClientDetailsConnector returns false" when {

        "UserClientDetailsConnector returns nothing when checking if outstanding work items exist" should {
          "return nothing" in new TestScope {
            mockUserClientDetailsConnectorCheckGroupAssignments(Some(false))
            mockUserClientDetailsConnectorOutstandingWorkItemsExist(None)

            optedInStatusHandler.identifyStatus(arn).futureValue shouldBe None
          }
        }

        "UserClientDetailsConnector returns some value when checking if outstanding work items exist" when {

          "UserClientDetailsConnector returns true" should {
            s"return $OptedInNotReady" in new TestScope {
              mockUserClientDetailsConnectorCheckGroupAssignments(Some(false))
              mockUserClientDetailsConnectorOutstandingWorkItemsExist(Some(true))

              optedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedInNotReady)
            }
          }

          "UserClientDetailsConnector returns false" should {
            s"return $OptedInReady" in new TestScope {
              mockUserClientDetailsConnectorCheckGroupAssignments(Some(false))
              mockUserClientDetailsConnectorOutstandingWorkItemsExist(Some(false))

              optedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedInReady)
            }
          }
        }
      }
    }
  }

}
