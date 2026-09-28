package org.gotson.komga.oracle.infrastructure.web

import org.gotson.komga.infrastructure.web.BracketParamsRequestWrapper
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class BracketParamsRequestWrapperOracleTest : OracleTest() {
  private fun wrap(query: String?) = BracketParamsRequestWrapper(WebOracle.request(query = query))

  private val queries =
    listOf(
      null,
      "a=1",
      "a[]=1",
      "a=1&a[]=2",
      "a[]=2&a=1",
      "a=1&a=2&a[]=3&a[]=4",
      "a[]=&a=",
      "a[][]=1",
      "b=x&a[]=1&c[]=y&a=2",
      "%5B%5D=1",
      "a%5B%5D=1",
    )

  override fun cases() {
    func("getParameter") {
      queries.forEach { q ->
        case("$q") { listOf(wrap(q).getParameter("a"), wrap(q).getParameter("a[]"), wrap(q).getParameter("a[][]"), wrap(q).getParameter("")) }
      }
    }
    func("getParameterValues") {
      queries.forEach { q ->
        case("$q") { listOf(wrap(q).getParameterValues("a"), wrap(q).getParameterValues("a[]"), wrap(q).getParameterValues("a[][]"), wrap(q).getParameterValues("")) }
      }
    }
    func("getParameterNames") {
      queries.forEach { q -> case("$q") { wrap(q).parameterNames.toList() } }
    }
    func("getParameterMap") {
      queries.forEach { q -> case("$q") { wrap(q).parameterMap } }
    }
  }
}
