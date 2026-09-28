package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.infrastructure.jooq.UnpagedSorted
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.Sort

class UnpagedSortedOracleTest : OracleTest() {
  private val p = UnpagedSorted(Sort.by(Sort.Order.desc("a"), Sort.Order.asc("b")))
  private val unsorted = UnpagedSorted(Sort.unsorted())

  override fun cases() {
    func("getPageNumber") {
      case("throws") { p.pageNumber }
      case("unsorted throws") { unsorted.pageNumber }
    }
    func("hasPrevious") {
      case("false") { p.hasPrevious() }
    }
    func("getSort") {
      case("sort") { p.sort.toString() }
      case("orders") { p.sort.toList().map { listOf(it.property, it.direction, it.isIgnoreCase, it.nullHandling) } }
      case("same instance") { p.sort === p.sort }
      case("unsorted") { listOf(unsorted.sort.isUnsorted, unsorted.sort.toString()) }
      case("getSortOr with sort") { p.getSortOr(Sort.by("x")).toString() }
      case("getSortOr unsorted") { unsorted.getSortOr(Sort.by("x")).toString() }
    }
    func("isPaged") {
      case("false") { p.isPaged }
      case("isUnpaged") { p.isUnpaged }
    }
    func("next") {
      case("same instance") { p.next() === p }
    }
    func("getPageSize") {
      case("throws") { p.pageSize }
    }
    func("getOffset") {
      case("throws") { p.offset }
    }
    func("first") {
      case("same instance") { p.first() === p }
    }
    func("withPage") {
      case("same instance") { p.withPage(3) === p }
      case("negative page") { p.withPage(-1) === p }
    }
    func("previousOrFirst") {
      case("same instance") { p.previousOrFirst() === p }
      case("chained") {
        p
          .next()
          .previousOrFirst()
          .first()
          .withPage(2)
          .sort
          .toString()
      }
    }
  }
}
