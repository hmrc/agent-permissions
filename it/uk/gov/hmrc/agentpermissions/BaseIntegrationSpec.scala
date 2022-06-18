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

package uk.gov.hmrc.agentpermissions

import com.google.inject.AbstractModule
import com.kenshoo.play.metrics.PlayModule
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Environment}

abstract class BaseIntegrationSpec
    extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite {

  protected lazy val conf: Configuration = GuiceApplicationBuilder().configuration
  protected lazy val env: Environment = GuiceApplicationBuilder().environment

  /** Child classes can override per their requirements
    */
  def moduleOverrides: AbstractModule = new AbstractModule {}

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .disable[PlayModule]
      .configure("metrics.enabled" -> false)
      .configure("auditing.enabled" -> false)
      .overrides(moduleOverrides)
      .build()

}
