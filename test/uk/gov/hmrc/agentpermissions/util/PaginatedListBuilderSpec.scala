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

package uk.gov.hmrc.agentpermissions.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import uk.gov.hmrc.agentpermissions.model.accessgroups.Client

class PaginatedListBuilderSpec extends AnyWordSpecLike with Matchers {

  val clients: Seq[Client] = (1 to 29) map (index => Client(s"EK$index", s"name$index"))
  val pageSize = 10

  "PaginatedListBuilder" should {

    "build first page correctly" in {
      val page = 1

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, clients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe false
      paginationMetaData.firstPage shouldBe true
      paginationMetaData.totalSize shouldBe clients.size
      paginationMetaData.totalPages shouldBe 3
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe pageSize

      paginatedClients.pageContent.size shouldBe pageSize
    }

    "build middle page correctly" in {
      val page = 2

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, clients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe false
      paginationMetaData.firstPage shouldBe false
      paginationMetaData.totalSize shouldBe clients.size
      paginationMetaData.totalPages shouldBe 3
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe pageSize

      paginatedClients.pageContent.size shouldBe pageSize
    }

    "build last page correctly" in {
      val page = 3

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, clients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe true
      paginationMetaData.firstPage shouldBe false
      paginationMetaData.totalSize shouldBe clients.size
      paginationMetaData.totalPages shouldBe 3
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe (clients.size - (2 * pageSize))

      paginatedClients.pageContent.size shouldBe (clients.size - (2 * pageSize))
    }

    "build zeroth page correctly" in {
      val page = 0

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, clients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe false
      paginationMetaData.firstPage shouldBe false
      paginationMetaData.totalSize shouldBe clients.size
      paginationMetaData.totalPages shouldBe 3
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe 0

      paginatedClients.pageContent.size shouldBe 0
    }

    "build last+1 page correctly" in {
      val page = 4

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, clients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe false
      paginationMetaData.firstPage shouldBe false
      paginationMetaData.totalSize shouldBe clients.size
      paginationMetaData.totalPages shouldBe 3
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe 0

      paginatedClients.pageContent.size shouldBe 0
    }

    "build page correctly for no clients" in {
      val page = 1

      val emptyClients = Seq.empty[Client]

      val paginatedClients = PaginatedListBuilder.build[Client](page, pageSize, emptyClients)
      val paginationMetaData = paginatedClients.paginationMetaData

      paginationMetaData.lastPage shouldBe false
      paginationMetaData.firstPage shouldBe true
      paginationMetaData.totalSize shouldBe 0
      paginationMetaData.totalPages shouldBe 0
      paginationMetaData.pageSize shouldBe pageSize
      paginationMetaData.currentPageNumber shouldBe page
      paginationMetaData.currentPageSize shouldBe 0

      paginatedClients.pageContent.size shouldBe 0
    }
  }
}
