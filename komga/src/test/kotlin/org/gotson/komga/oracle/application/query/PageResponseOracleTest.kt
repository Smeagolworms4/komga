package org.gotson.komga.oracle.application.query

import org.gotson.komga.application.query.PageResponse
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class PageResponseOracleTest : OracleTest() {
  private val responses =
    linkedMapOf(
      "first of many" to PageResponse(listOf("a"), 0, 20, 105, 6),
      "middle" to PageResponse(listOf("a"), 2, 20, 105, 6),
      "last" to PageResponse(listOf("a"), 5, 20, 105, 6),
      "beyond last" to PageResponse(emptyList(), 9, 20, 105, 6),
      "single page" to PageResponse(listOf(1, 2), 0, 2, 2, 1),
      "empty" to PageResponse(emptyList<String>(), 0, 20, 0, 0),
      "empty second page" to PageResponse(emptyList<String>(), 1, 20, 0, 0),
    )

  override fun cases() {
    func("isFirst") { responses.forEach { (n, r) -> case(n) { r.isFirst } } }
    func("isLast") { responses.forEach { (n, r) -> case(n) { r.isLast } } }
    func("json") { responses.forEach { (n, r) -> case(n) { WebOracle.stableText(WebOracle.mapper.writeValueAsString(r)) } } }
  }
}
