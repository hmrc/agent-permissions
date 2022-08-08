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

import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentpermissions.BaseSpec
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends BaseSpec {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val appConfig = new AppConfigImpl(new ServicesConfig(configuration))

  "App config" should {
    "be set up correctly" in {
      appConfig.agentUserClientDetailsBaseUrl shouldBe "http://localhost:9449"
      appConfig.agentSizeMaxClientCountAllowed shouldBe 900
    }
  }
}
