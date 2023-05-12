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
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.agentpermissions.connectors.UserClientDetailsConnector
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class NotOptedInStatusHandlerSpec extends BaseSpec {

  trait TestScope {

    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockUserClientDetailsConnector: UserClientDetailsConnector = mock[UserClientDetailsConnector]
    implicit val mockAppConfig: AppConfig = mock[AppConfig]
    val notOptedInStatusHandler = new NotOptedInStatusHandlerImpl(mockUserClientDetailsConnector)

    def mockUserClientDetailsConnectorAgentSize(
      maybeSize: Option[Int]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Int]]] =
      (mockUserClientDetailsConnector
        .agentSize(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future.successful(maybeSize))

    def mockAppConfigAgentSizeMaxClientCountAllowed(max: Int) =
      (mockAppConfig.agentSizeMaxClientCountAllowed _: () => Int)
        .expects()
        .returning(max)

    def mockUserClientDetailsConnectorCheckGroupAssignments(
      maybeSingleUser: Option[Boolean]
    ): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Option[Boolean]]] =
      (mockUserClientDetailsConnector
        .isSingleUserAgency(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, headerCarrier, executionContext)
        .returning(Future.successful(maybeSingleUser))
  }

  "identifyStatus" when {

    "UserClientDetailsConnector returns nothing when fetching agent size" should {
      "return nothing" in new TestScope {
        mockUserClientDetailsConnectorAgentSize(None)

        notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe None
      }
    }

    "UserClientDetailsConnector returns some value when fetching agent size" when {

      "UserClientDetailsConnector returns less than two" should {
        s"return $OptedOutWrongClientCount" in new TestScope {
          mockUserClientDetailsConnectorAgentSize(Some(1))

          notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedOutWrongClientCount)
        }
      }

      "UserClientDetailsConnector returns at least two" when {

        "UserClientDetailsConnector returns more than the max client count allowed in config" should {

          s"return $OptedOutWrongClientCount" in new TestScope {
            mockUserClientDetailsConnectorAgentSize(Some(1000))
            mockAppConfigAgentSizeMaxClientCountAllowed(10)

            notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedOutWrongClientCount)
          }
        }

        "UserClientDetailsConnector returns less than the max client count allowed in config" when {

          "UserClientDetailsConnector returns nothing when checking if single user agency" should {
            s"return nothing" in new TestScope {
              mockUserClientDetailsConnectorAgentSize(Some(2))
              mockAppConfigAgentSizeMaxClientCountAllowed(10)
              mockUserClientDetailsConnectorCheckGroupAssignments(None)

              notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe None
            }
          }

          "UserClientDetailsConnector returns some value when checking if single user agency" when {

            "UserClientDetailsConnector returns true" should {
              s"return $OptedOutSingleUser" in new TestScope {
                mockUserClientDetailsConnectorAgentSize(Some(2))
                mockAppConfigAgentSizeMaxClientCountAllowed(10)
                mockUserClientDetailsConnectorCheckGroupAssignments(Some(true))

                notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedOutSingleUser)
              }
            }

            "UserClientDetailsConnector returns false" should {
              s"return $OptedOutEligible" in new TestScope {
                mockUserClientDetailsConnectorAgentSize(Some(2))
                mockAppConfigAgentSizeMaxClientCountAllowed(10)
                mockUserClientDetailsConnectorCheckGroupAssignments(Some(false))

                notOptedInStatusHandler.identifyStatus(arn).futureValue shouldBe Some(OptedOutEligible)
              }
            }
          }
        }
      }
    }
  }

}
