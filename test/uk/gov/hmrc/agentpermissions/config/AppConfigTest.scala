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

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.{Configuration, Environment}

class AppConfigTest extends AnyWordSpecLike with should.Matchers {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val appConfig = new AppConfig(configuration)

  "App config" should {
    "give correct app name" in {
      appConfig.appName shouldBe "agent-permissions"
    }
  }
}
