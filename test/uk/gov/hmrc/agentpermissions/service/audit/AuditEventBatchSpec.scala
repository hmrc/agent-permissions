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

package uk.gov.hmrc.agentpermissions.service.audit

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.agentpermissions.BaseSpec

import java.util.UUID

class AuditEventBatchSpec extends BaseSpec {

  case class TestObject(name: String)
  object TestObject {
    implicit val writes: Writes[TestObject] = Json.writes[TestObject]
  }

  "Batch creation" should {
    "construct parts correctly" in {
      val batchSize = 100

      val batch = AuditEventBatch.make((1 to batchSize).map(n => TestObject(s"name$n")))

      batch.parts.size shouldBe batchSize

      val seqBatchId = batch.parts.map(_.batchId).distinct
      seqBatchId.size shouldBe 1
      UUID.fromString(seqBatchId.head).toString shouldBe seqBatchId.head

      batch.parts.foreach { part =>
        val json = part.json
        val batchPart = (json \ "batchPart").get
        (batchPart \ "partOutOfTotal").as[Int] shouldBe batchSize
        val partNumber = (batchPart \ "partNumber").as[Int]
        (batchPart \ "value" \ "name").as[String] shouldBe s"name$partNumber"
      }
    }
  }

}
