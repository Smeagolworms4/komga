package org.gotson.komga.oracle.infrastructure.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.gotson.komga.infrastructure.web.EtagFilterConfiguration
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.web.filter.ShallowEtagHeaderFilter

class EtagFilterConfigurationOracleTest : OracleTest() {
  private val bean = EtagFilterConfiguration().shallowEtagHeaderFilter()
  private val filter = bean.filter

  private fun shouldNotFilter(uri: String): Boolean {
    val m = filter.javaClass.getDeclaredMethod("shouldNotFilter", HttpServletRequest::class.java)
    m.isAccessible = true
    return m.invoke(filter, WebOracle.request(uri = uri)) as Boolean
  }

  private fun run(
    uri: String,
    body: String = "hello",
    headers: List<Pair<String, String>> = emptyList(),
  ): List<Any?> {
    val response = WebOracle.response()
    filter.doFilter(
      WebOracle.request(uri = uri, headers = headers),
      response,
      FilterChain { _, res ->
        res.contentType = "text/plain"
        res.outputStream.write(body.toByteArray(Charsets.UTF_8))
      },
    )
    return WebOracle.describeResponse(response)
  }

  override fun cases() {
    func("shallowEtagHeaderFilter") {
      case("url patterns") { bean.urlPatterns.toList() }
      case("name") { bean.filterName }
      case("order") { bean.order }
      case("is a ShallowEtagHeaderFilter") { filter is ShallowEtagHeaderFilter }
      case("etag on api") { run("/api/v1/books") }
      case("etag on empty body") { run("/api/v1/books", body = "") }
      case("etag unicode body") { run("/opds/v1.2/catalog", body = "ünï 漫画") }
      case("not modified") { run("/api/v1/books", headers = listOf("If-None-Match" to "\"05d41402abc4b2a76b9719d911017c592\"")) }
      case("excluded file download") { run("/api/v1/books/B1/file") }
    }
    func("shouldNotFilter") {
      listOf(
        "/api/v1/books/B1/file",
        "/api/v1/books/B1/file/",
        "/api/v1/books/B1/file/name.cbz",
        "/api/v1/books/B1/file/a/b",
        "/api/v1/books/B1/files",
        "/api/v1/books/B1/B2/file",
        "/api/v1/books//file",
        "/api/v1/books/B1",
        "/opds/v1.2/books/B1/file/x",
        "/opds/v2/books/B1/file",
        "/api/v1/readlists/R1/file",
        "/api/v1/series/S1/file",
        "/api/v1/collections/C1/file",
        "/kobo/KEY/v1/books/B1/file/epub",
        "/kobo/KEY/v1/books/B1/thumbnail",
        "/kobo/v1/books/B1/file",
        "/API/v1/books/B1/file",
        "/api/v1/books/B%201/file",
        "/",
      ).forEach { case(it) { shouldNotFilter(it) } }
    }
  }
}
