package org.gotson.komga.oracle.application.query

import org.gotson.komga.application.query.PageResult
import org.gotson.komga.application.query.toPageResponse
import org.gotson.komga.application.query.toPageResult
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class PageResultOracleTest : OracleTest() {
  private val items = PageResult(listOf("a", "b", "c"), 105)

  override fun cases() {
    func("toPageResponse@12") {
      case("paged first page") { items.toPageResponse(PageRequest.of(0, 20)) { it.uppercase() } }
      case("paged last page") { PageResult(listOf("x"), 41).toPageResponse(PageRequest.of(2, 20)) { "$it!" } }
      case("unpaged") { items.toPageResponse(Pageable.unpaged()) { it.length } }
      case("empty paged") { PageResult(emptyList<String>(), 0).toPageResponse(PageRequest.of(0, 10)) { it } }
      case("empty unpaged") { PageResult(emptyList<String>(), 0).toPageResponse(Pageable.unpaged()) { it } }
      case("page beyond") { PageResult(emptyList<String>(), 5).toPageResponse(PageRequest.of(3, 2)) { it } }
      case("exact pages") { PageResult(listOf(1, 2), 4).toPageResponse(PageRequest.of(1, 2)) { it * 10 } }
    }
    func("toPageResponse@29") {
      case("identity") { items.toPageResponse(PageRequest.of(1, 50)) }
      case("unpaged identity") { PageResult(listOf(1), 1).toPageResponse(Pageable.unpaged()) }
    }
    func("toPageResult") {
      case("paged") { PageImpl(listOf("a", "b"), PageRequest.of(1, 2), 9).toPageResult() }
      case("unpaged") { PageImpl(listOf("a", "b")).toPageResult() }
      case("empty") { PageImpl(emptyList<String>()).toPageResult() }
    }
  }
}
