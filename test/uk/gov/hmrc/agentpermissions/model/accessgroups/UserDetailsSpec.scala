/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.model.accessgroups

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.libs.json.Json

class UserDetailsSpec extends AnyFlatSpec with Matchers {

  val testUserDetails: UserDetails = UserDetails(
    userId = Some("user123"),
    credentialRole = Some("Agent"),
    name = Some("Test User"),
    email = Some("test.user@example.com")
  )

  val json: JsValue = Json.toJson(testUserDetails)

  "UserDetails" should "serialise to JSON" in {
    json.toString shouldBe
      """{"userId":"user123","credentialRole":"Agent","name":"Test User","email":"test.user@example.com"}"""
  }

  it should "deserialise from JSON" in {
    json.as[UserDetails] shouldBe testUserDetails
  }

  it should "omit optional empty fields when serialising to JSON" in {
    val userDetailsWithEmptyFields = UserDetails(
      userId = Some("user123")
    )

    Json.toJson(userDetailsWithEmptyFields).toString shouldBe
      """{"userId":"user123"}"""
  }

  it should "deserialise JSON with missing optional fields" in {
    val jsonWithMissingFields =
      Json.parse("""{"userId":"user123"}""")

    jsonWithMissingFields.as[UserDetails] shouldBe UserDetails(
      userId = Some("user123"),
      credentialRole = None,
      name = None,
      email = None
    )
  }

  it should "create UserDetails with default empty values" in {
    UserDetails() shouldBe UserDetails(
      userId = None,
      credentialRole = None,
      name = None,
      email = None
    )
  }
}
