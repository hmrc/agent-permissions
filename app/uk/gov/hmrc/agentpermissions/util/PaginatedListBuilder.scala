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

import uk.gov.hmrc.agentpermissions.model.PaginatedList
import uk.gov.hmrc.agentpermissions.model.PaginationMetaData

object PaginatedListBuilder {

  def build[T](page: Int, pageSize: Int, fullList: Seq[T]): PaginatedList[T] = {
    val pageStart = (page - 1) * pageSize
    val pageEnd = pageStart + pageSize
    val numberOfPages = Math.ceil(fullList.length.toDouble / pageSize.toDouble).toInt
    val pageSliceUntil = Math.min(pageEnd, if (numberOfPages == page) fullList.length else fullList.length - 1)

    val currentPageContent = fullList.slice(pageStart, pageSliceUntil)

    PaginatedList[T](
      pageContent = currentPageContent,
      paginationMetaData = PaginationMetaData(
        firstPage = page == 1,
        lastPage = numberOfPages == page,
        totalSize = fullList.length,
        pageSize = pageSize,
        totalPages = numberOfPages,
        currentPageNumber = page,
        currentPageSize = currentPageContent.length
      )
    )
  }
}
