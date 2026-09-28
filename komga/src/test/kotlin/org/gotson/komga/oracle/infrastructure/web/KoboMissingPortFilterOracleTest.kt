package org.gotson.komga.oracle.infrastructure.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gotson.komga.infrastructure.web.KoboMissingPortFilter
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class KoboMissingPortFilterOracleTest : OracleTest() {
  private fun call(
    filter: KoboMissingPortFilter,
    name: String,
    vararg args: Any,
  ): Any? {
    val types = args.map { if (it is HttpServletRequest) HttpServletRequest::class.java else if (it is HttpServletResponse) HttpServletResponse::class.java else FilterChain::class.java }
    val m =
      generateSequence<Class<*>>(filter.javaClass) { it.superclass }
        .mapNotNull { c -> c.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.toList() == types } }
        .first()
    m.isAccessible = true
    return m.invoke(filter, *args)
  }

  private fun seenPort(
    port: Int?,
    request: HttpServletRequest,
    nested: Boolean = false,
  ): List<Any?> {
    val seen = mutableListOf<Any?>()
    val response = WebOracle.response()
    val chain = FilterChain { req, _ -> seen.add((req as HttpServletRequest).serverPort) }
    val filter = KoboMissingPortFilter { port }
    if (nested) call(filter, "doFilterNestedErrorDispatch", request, response, chain) else filter.doFilter(request, response, chain)
    return listOf(seen, response.status)
  }

  private val filter = KoboMissingPortFilter { 1234 }

  override fun cases() {
    func("shouldNotFilter") {
      case("no header") { call(filter, "shouldNotFilter", WebOracle.request()) }
      listOf("Forwarded" to "for=1.2.3.4", "X-Forwarded-Host" to "example.org", "X-Forwarded-Port" to "8443", "X-Forwarded-Proto" to "https", "X-Forwarded-Prefix" to "/prefix", "X-Forwarded-Ssl" to "on", "X-Forwarded-For" to "1.2.3.4", "x-forwarded-host" to "example.org", "X-Real-IP" to "1.2.3.4", "X-Forwarded-Server" to "proxy")
        .forEach { (h, v) -> case(h) { call(filter, "shouldNotFilter", WebOracle.request(headers = listOf(h to v))) } }
      case("empty header value") { call(filter, "shouldNotFilter", WebOracle.request(headers = listOf("X-Forwarded-Port" to ""))) }
    }
    func("shouldNotFilterAsyncDispatch") {
      case("false") { call(filter, "shouldNotFilterAsyncDispatch") }
    }
    func("shouldNotFilterErrorDispatch") {
      case("false") { call(filter, "shouldNotFilterErrorDispatch") }
    }
    func("doFilterInternal") {
      case("port supplied") { seenPort(1234, WebOracle.request(uri = "/kobo/k/v1/library/sync")) }
      case("no port supplied, request port") { seenPort(null, WebOracle.request(uri = "/kobo/k", port = 8080)) }
      case("no port supplied, default port") { seenPort(null, WebOracle.request(uri = "/kobo/k")) }
      case("port supplied, https request") { seenPort(25600, WebOracle.request(uri = "/kobo/k", scheme = "https", port = 443)) }
      case("forwarded header: not filtered") { seenPort(1234, WebOracle.request(uri = "/kobo/k", port = 8080, headers = listOf("X-Forwarded-For" to "1.2.3.4"))) }
      case("port 0 supplied") { seenPort(0, WebOracle.request(uri = "/kobo/k", port = 8080)) }
    }
    func("doFilterNestedErrorDispatch") {
      case("port supplied") { seenPort(4321, WebOracle.request(uri = "/kobo/k"), nested = true) }
      case("no port supplied") { seenPort(null, WebOracle.request(uri = "/kobo/k", port = 9000), nested = true) }
    }
    func("formatRequest") {
      case("get") { call(filter, "formatRequest", WebOracle.request(uri = "/kobo/k/v1/library/sync")) }
      case("post with encoded uri") { call(filter, "formatRequest", WebOracle.request(method = "POST", uri = "/kobo/a%20b/%C3%BC")) }
    }
    func("getServerPort") {
      case("supplier called on each access") {
        var n = 0
        val seen = mutableListOf<Int>()
        KoboMissingPortFilter { ++n }.doFilter(
          WebOracle.request(),
          WebOracle.response(),
          FilterChain { req, _ ->
            seen.add((req as HttpServletRequest).serverPort)
            seen.add(req.serverPort)
          },
        )
        seen
      }
      case("other properties unchanged") {
        val seen = mutableListOf<Any?>()
        KoboMissingPortFilter { 1 }.doFilter(
          WebOracle.request(uri = "/kobo/x", scheme = "https", host = "example.org", port = 8443),
          WebOracle.response(),
          FilterChain { req, _ ->
            val r = req as HttpServletRequest
            seen.addAll(listOf(r.serverPort, r.serverName, r.scheme, r.requestURI, r.requestURL.toString(), r.isSecure))
          },
        )
        seen
      }
    }
  }
}
