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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentpermissions.BaseSpec

class GroupIdSpec extends BaseSpec {

  val validArn = "KARN0762398"
  val invalidArn = "KARN0101010"
  val tildeEncoded = "%7E"
  val groupNameEncoded = "some+group"
  val groupNameDecoded = "some group"
  val whitespace = " "

  "Calling decode" when {

    "gid is null" should {
      "return nothing" in {
        GroupId.decode(null) shouldBe None
      }
    }

    "gid is empty" should {
      "return nothing" in {
        GroupId.decode("") shouldBe None
      }
    }

    "gid only has one part" should {
      "return nothing" in {
        GroupId.decode("some string") shouldBe None
      }
    }

    "gid has empty group name part" should {
      "return nothing" in {
        GroupId.decode(s"$validArn$tildeEncoded$whitespace") shouldBe None
      }
    }

    "gid has empty arn part" should {
      "return nothing" in {
        GroupId.decode(s"$whitespace$tildeEncoded$groupNameEncoded") shouldBe None
      }
    }

    "gid has both empty parts" should {
      "return nothing" in {
        GroupId.decode(s"$whitespace$tildeEncoded$whitespace") shouldBe None
      }
    }

    "gid has both valid parts" should {
      "return Some" in {
        GroupId.decode(s"$validArn$tildeEncoded$groupNameEncoded") shouldBe
          Some(GroupId(Arn(validArn), groupNameDecoded))
      }
    }

    "gid has invalid arn part" should {
      "return nothing" in {
        GroupId.decode(s"$invalidArn$tildeEncoded$groupNameEncoded") shouldBe None
      }
    }

    "gid has leading whitespace" should {
      "return nothing" in {
        GroupId.decode(s"$whitespace$validArn$tildeEncoded$groupNameEncoded") shouldBe None
      }
    }

    "gid has trailing whitespace" should {
      "return nothing" in {
        GroupId.decode(s"$validArn$tildeEncoded$groupNameEncoded$whitespace") shouldBe None
      }
    }
  }

}
