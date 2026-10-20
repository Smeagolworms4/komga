package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.domain.model.Media
import org.gotson.komga.interfaces.api.getBookLastModified
import org.gotson.komga.interfaces.api.setNotModified
import org.gotson.komga.interfaces.api.unpackPageParams
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

class UtilsOracleTest : OracleTest() {
  private fun media(date: LocalDateTime) = Media(createdDate = date, lastModifiedDate = date)

  private val dates =
    listOf(
      LocalDateTime.of(2020, 1, 2, 3, 4, 5),
      LocalDateTime.of(2020, 1, 2, 3, 4, 5, 678_000_000),
      LocalDateTime.of(2020, 1, 2, 3, 4, 5, 999_999_999),
      LocalDateTime.of(1970, 1, 1, 0, 0),
      LocalDateTime.of(1969, 12, 31, 23, 59, 59, 500_000_000),
      LocalDateTime.of(2021, 3, 28, 2, 30),
      LocalDateTime.of(9999, 12, 31, 23, 59, 59),
    )

  private fun describe(p: Pageable) = listOf(p.isPaged, if (p.isPaged) p.pageNumber else null, if (p.isPaged) p.pageSize else null, p.sort.toString())

  override fun cases() {
    func("getBookLastModified") {
      dates.forEach { case("$it") { getBookLastModified(media(it)) } }
    }
    func("setNotModified") {
      dates.forEach { case("$it") { WebOracle.describeEntity(ResponseEntity.ok().setNotModified(media(it)).build<Any>()) } }
    }
    func("unpackPageParams") {
      case("paged") { describe(unpackPageParams(false, PageRequest.of(2, 15))) }
      case("unpaged flag") { describe(unpackPageParams(true, PageRequest.of(2, 15))) }
      case("sort dropped") { describe(unpackPageParams(false, PageRequest.of(0, 5, Sort.by(Sort.Order.desc("name"))))) }
      case("unpaged flag with sort") { describe(unpackPageParams(true, PageRequest.of(0, 5, Sort.by("name")))) }
      case("unpaged input") { describe(unpackPageParams(true, Pageable.unpaged())) }
    }
  }
}
