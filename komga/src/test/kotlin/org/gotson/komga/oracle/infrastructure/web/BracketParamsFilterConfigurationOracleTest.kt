package org.gotson.komga.oracle.infrastructure.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.gotson.komga.infrastructure.web.BracketParamsFilterConfiguration
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class BracketParamsFilterConfigurationOracleTest : OracleTest() {
  private val bean = BracketParamsFilterConfiguration().bracketParamsFilter()

  private fun filter(query: String?): List<Any?> {
    val seen = mutableListOf<Any?>()
    bean.filter.doFilter(
      WebOracle.request(uri = "/api/v1/series", query = query),
      WebOracle.response(),
      FilterChain { req, _ ->
        val r = req as HttpServletRequest
        seen.add(r::class.java.simpleName)
        seen.add(r.getParameterValues("library_id")?.toList())
        seen.add(r.getParameter("library_id"))
      },
    )
    return seen
  }

  override fun cases() {
    func("bracketParamsFilter") {
      case("url patterns") { bean.urlPatterns.toList() }
      case("name") { bean.filterName }
      case("order") { bean.order }
      case("filter class") { bean.filter::class.java.simpleName }
      case("new bean each call") { BracketParamsFilterConfiguration().bracketParamsFilter() !== bean }
    }
    func("doFilter") {
      case("no parameter") { filter(null) }
      case("plain parameter") { filter("library_id=A") }
      case("bracket parameter") { filter("library_id[]=A&library_id[]=B") }
      case("both") { filter("library_id=A&library_id[]=B") }
      case("null chain") { bean.filter.doFilter(WebOracle.request(), WebOracle.response(), null) }
    }
  }
}
