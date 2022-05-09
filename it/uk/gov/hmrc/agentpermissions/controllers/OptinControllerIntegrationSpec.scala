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

package uk.gov.hmrc.agentpermissions.controllers

import com.google.inject.AbstractModule
import play.api.http.Status._
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedIn, OptedOut, OptinRecord}
import uk.gov.hmrc.agentpermissions.BaseIntegrationSpec
import uk.gov.hmrc.agentpermissions.repository.OptinRepository
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class OptinControllerIntegrationSpec extends BaseIntegrationSpec with DefaultPlayMongoRepositorySupport[OptinRecord] {

  val arn = "TARN0000001"

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(
    EnrolmentIdentifier(agentReferenceNumberIdentifier, arn)
  )
  val agentEnrolment = "HMRC-AS-AGENT"

  override def moduleOverrides: AbstractModule = new AbstractModule {
    override def configure(): Unit = {
      bind(classOf[OptinRepository]).toInstance(repository.asInstanceOf[OptinRepository])
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
    }
  }

  trait TestScope {
    lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
    lazy val baseUrl = s"http://localhost:$port"
  }

  "Call to optin" when {

    "optin record does not already exist" should {
      s"return $CREATED" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe CREATED
      }
    }

    s"optin record already exists having $OptedOut type" should {
      s"return $CREATED" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe CREATED

        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe CREATED
      }
    }

    s"optin record already exists having $OptedIn type" should {
      s"return $CONFLICT" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe CREATED

        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe CONFLICT
      }
    }

    s"arn provided is different than that identified by auth" should {
      s"return $BAD_REQUEST" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)

        val someOtherArn = "KARN0101010"
        wsClient
          .url(s"$baseUrl/agent-permissions/arn/$someOtherArn/optin")
          .post("")
          .futureValue
          .status shouldBe BAD_REQUEST
      }
    }

    s"request is not authorised due to empty enrolments" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingEmptyEnrolments)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect credential role" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentialRole)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect username" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectUsername)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect credentials" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentials)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"an appropriate bearer token is not found in the request" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithException(new MissingBearerToken)

        val response: WSResponse = wsClient
          .url(s"$baseUrl/agent-permissions/arn/$arn/optin")
          .withFollowRedirects(false)
          .post("")
          .futureValue

        response.status shouldBe FORBIDDEN
      }
    }

    s"a runtime exception is thrown" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        stubAuthResponseWithException(new InsufficientEnrolments)

        val response: WSResponse = wsClient
          .url(s"$baseUrl/agent-permissions/arn/$arn/optin")
          .post("")
          .futureValue
        response.status shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "Call to optout" when {

    "optin record does not already exist" should {
      s"return $CREATED" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe CREATED
      }
    }

    s"optin record already exists having $OptedIn type" should {
      s"return $CREATED" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optin").post("").futureValue.status shouldBe CREATED

        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe CREATED
      }
    }

    s"optin record already exists having $OptedOut type" should {
      s"return $CONFLICT" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe CREATED

        stubAuthResponseWithoutException(buildAuthorisedResponse)
        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe CONFLICT
      }
    }

    s"arn provided is different than that identified by auth" should {
      s"return $BAD_REQUEST" in new TestScope {
        stubAuthResponseWithoutException(buildAuthorisedResponse)

        val someOtherArn = "KARN0101010"
        wsClient
          .url(s"$baseUrl/agent-permissions/arn/$someOtherArn/optout")
          .post("")
          .futureValue
          .status shouldBe BAD_REQUEST
      }
    }

    s"request is not authorised due to empty enrolments" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingEmptyEnrolments)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect credential role" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentialRole)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect username" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectUsername)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"request is not authorised due to incorrect credentials" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithoutException(buildUnauthorisedResponseHavingIncorrectCredentials)

        wsClient.url(s"$baseUrl/agent-permissions/arn/$arn/optout").post("").futureValue.status shouldBe FORBIDDEN
      }
    }

    s"an appropriate bearer token is not found in the request" should {
      s"return $FORBIDDEN" in new TestScope {
        stubAuthResponseWithException(new MissingBearerToken)

        val response: WSResponse = wsClient
          .url(s"$baseUrl/agent-permissions/arn/$arn/optout")
          .withFollowRedirects(false)
          .post("")
          .futureValue

        response.status shouldBe FORBIDDEN
      }
    }

    s"a runtime exception is thrown" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestScope {
        stubAuthResponseWithException(new InsufficientEnrolments)

        val response: WSResponse = wsClient
          .url(s"$baseUrl/agent-permissions/arn/$arn/optout")
          .post("")
          .futureValue
        response.status shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  private def buildAuthorisedResponse: GrantAccess =
    Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and
      Some(User) and
      Some(Name(Some("Jane"), Some("Doe"))) and
      Some(Credentials("user1", "GovernmentGateway"))

  private def buildUnauthorisedResponseHavingEmptyEnrolments: GrantAccess =
    Enrolments(Set.empty) and
      Some(User) and
      Some(Name(Some("Jane"), Some("Doe"))) and
      Some(Credentials("user1", "GovernmentGateway"))

  private def buildUnauthorisedResponseHavingIncorrectCredentialRole: GrantAccess =
    Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and
      None and
      Some(Name(Some("Jane"), Some("Doe"))) and
      Some(Credentials("user1", "GovernmentGateway"))

  private def buildUnauthorisedResponseHavingIncorrectUsername: GrantAccess =
    Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and
      Some(User) and
      None and
      Some(Credentials("user1", "GovernmentGateway"))

  private def buildUnauthorisedResponseHavingIncorrectCredentials: GrantAccess =
    Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and
      Some(User) and
      Some(Name(Some("Jane"), Some("Doe"))) and
      None

  override protected def repository: PlayMongoRepository[OptinRecord] = new OptinRepository(mongoComponent)
}
