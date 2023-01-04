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

package uk.gov.hmrc.agentpermissions.binders

import uk.gov.hmrc.agentpermissions.BaseSpec

class SimpleObjectBinderSpec extends BaseSpec {

  case class Example(str: String)

  "bind and unbind" when {

    "happy path" should {
      "correctly work" in {
        val bind: String => Example = param => Example(param)
        val unbind: Example => String = example => example.str
        val binder = new SimpleObjectBinder[Example](bind, unbind)
        binder.bind("key", "in") shouldBe Right(Example("in"))
        binder.unbind("key", Example("out")) shouldBe "out"
      }
    }

    "error path" should {
      "throw exceptions" in {
        val bind: String => Example = param => throw new RuntimeException("Bad")
        val unbind: Example => String = example => throw new RuntimeException("Sad")
        val binder = new SimpleObjectBinder[Example](bind, unbind)
        binder.bind("key", "in") shouldBe Left("Cannot parse parameter 'key' with value 'in' as 'Example'")
        assertThrows[RuntimeException](binder.unbind("key", Example("out")))
      }
    }
  }

}
