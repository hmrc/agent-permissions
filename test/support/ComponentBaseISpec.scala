/*
 * Copyright 2025 HM Revenue & Customs
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

package support

import it.stubs.{AgentUserClientDetailsStubs, AuthStubs}
import org.mongodb.scala.MongoDatabase
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{AnyContent, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.agentpermissions.TestConstants
import uk.gov.hmrc.agentpermissions.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.SingleObservableFuture

import scala.concurrent.ExecutionContext

trait ComponentBaseISpec
    extends UnitSpec with GuiceOneServerPerSuite with Injecting with AuthStubs with AgentUserClientDetailsStubs
    with WiremockHelper with BeforeAndAfterAll with BeforeAndAfterEach with TestConstants with IntegrationPatience {

  // Add all services required by our (or bootstrap's) connectors here
  def downstreamServices: Map[String, String] =
    Seq(
      "auth",
      "agent-user-client-details"
    ).flatMap { service =>
      Seq(
        s"microservice.services.$service.host" -> mockHost,
        s"microservice.services.$service.port" -> mockPort
      )
    }.toMap

  def extraConfig(): Map[String, Any] = Map.empty

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config ++ extraConfig() ++ downstreamServices)
    .build()

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: String = WiremockHelper.wiremockPort.toString
  val mockUrl: String = s"http://$mockHost:$mockPort"

  def config: Map[String, Any] = Map(
    "auditing.enabled"               -> false,
    "auditing.consumer.baseUri.host" -> mockHost,
    "auditing.consumer.baseUri.port" -> mockPort,
    "fieldLevelEncryption.enabled"   -> true,
    "features.check-arn-allow-list"  -> true,
    "allowed.arns"                   -> List(s"${arn.value}", "HARN0001")
  )

  val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]

  val mongoDatabase: MongoDatabase = mongoComponent.database

  protected def prepareDatabase(): Unit =
    mongoDatabase
      .drop()
      .toFuture()
      .futureValue

  private val ws: WSClient = app.injector.instanceOf[WSClient]
  given ExecutionContext = app.injector.instanceOf[ExecutionContext]
  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  protected val selfPath: String = "/agent-permissions"

  override def beforeAll(): Unit = {
    startWiremock()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    resetWiremock()
    prepareDatabase()
    super.beforeEach()
  }

  def get(uri: String): WSResponse = await(buildClient(uri).get())

  def post(uri: String)(body: JsValue): WSResponse = await(
    buildClient(uri)
      .post(body)
  )

  def delete(uri: String): WSResponse = await(buildClient(uri).delete())

  def patch(uri: String)(body: JsValue): WSResponse = await(
    buildClient(uri)
      .patch(body)
  )

  def put(uri: String)(body: JsValue): WSResponse = await(buildClient(uri).put(body))

  val baseUrl: String = "/agent-permissions"

  private def buildClient(path: String): WSRequest = ws
    .url(s"http://localhost:$port$baseUrl${path.replace(baseUrl, "")}")
    .withFollowRedirects(false)
    .addHttpHeaders(
      "Authorization" -> "Bearer xyz",
      "X-Session-ID"  -> "session-123",
      "Content-Type"  -> "application/json"
    )

  given Request[AnyContent] =
    FakeRequest().withHeaders()

  given hc: HeaderCarrier = HeaderCarrier()

}
