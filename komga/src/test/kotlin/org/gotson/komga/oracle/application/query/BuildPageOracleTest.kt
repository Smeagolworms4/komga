package org.gotson.komga.oracle.application.query

import org.gotson.komga.application.query.UnpagedSorted
import org.gotson.komga.application.query.buildPage
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class BuildPageOracleTest : OracleTest() {
  private fun page(p: Page<*>) = listOf(p, p.totalPages, p.sort.toString(), p.pageable.isPaged, p.hasNext())

  override fun cases() {
    func("buildPage") {
      case("paged, sort from pageable") { page(buildPage(listOf("a", "b"), PageRequest.of(1, 2, Sort.by("title")), 10, null)) }
      case("paged, explicit sort") { page(buildPage(listOf("a"), PageRequest.of(0, 5, Sort.by("title")), 1, Sort.by(Sort.Order.desc("age")))) }
      case("paged, count smaller than page") { page(buildPage(listOf("a", "b", "c"), PageRequest.of(2, 10), 5, null)) }
      case("paged, empty content") { page(buildPage(emptyList<String>(), PageRequest.of(3, 10), 5, null)) }
      case("unpaged, small count") { page(buildPage(listOf("a"), Pageable.unpaged(), 5, null)) }
      case("unpaged, large count") { page(buildPage(List(30) { "x$it" }, Pageable.unpaged(), 30, Sort.by("id"))) }
      case("unpaged, count 20") { page(buildPage(emptyList<String>(), Pageable.unpaged(), 20, null)) }
      case("unpaged, zero count") { page(buildPage(emptyList<String>(), Pageable.unpaged(), 0, null)) }
      case("unpaged sorted") { page(buildPage(listOf(1, 2, 3), UnpagedSorted(Sort.by(Sort.Order.asc("seriesId"), Sort.Order.desc("number"))), 3, null)) }
      case("unpaged sorted, explicit sort") { page(buildPage(listOf(1), UnpagedSorted(Sort.by("a")), 1, Sort.unsorted())) }
      case("unpaged sorted, large count") { page(buildPage(listOf(1), UnpagedSorted(Sort.unsorted()), 25, null)) }
    }
  }
}
