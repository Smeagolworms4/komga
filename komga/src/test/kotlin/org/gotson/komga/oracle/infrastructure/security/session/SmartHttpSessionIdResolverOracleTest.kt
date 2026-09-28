package org.gotson.komga.oracle.infrastructure.security.session

import org.gotson.komga.infrastructure.security.session.SmartHttpSessionIdResolver
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.session.web.http.DefaultCookieSerializer

class SmartHttpSessionIdResolverOracleTest : OracleTest() {
  private val resolver = SmartHttpSessionIdResolver("X-Auth-Token", DefaultCookieSerializer().apply { setCookieName("KOMGA-SESSION") })

  // "session-1" and "session-2" in base64
  private val b64one = "c2Vzc2lvbi0x"
  private val b64two = "c2Vzc2lvbi0y"

  private fun set(
    sessionId: String,
    vararg headers: Pair<String, String>,
    scheme: String = "http",
  ): List<Any?> {
    val response = WebOracle.response()
    resolver.setSessionId(WebOracle.request(uri = "/api/v1/login", headers = headers.toList(), scheme = scheme, port = if (scheme == "https") 443 else 80), response, sessionId)
    return WebOracle.describeResponse(response)
  }

  private fun expire(vararg headers: Pair<String, String>): List<Any?> {
    val response = WebOracle.response()
    resolver.expireSession(WebOracle.request(uri = "/api/logout", headers = headers.toList()), response)
    return WebOracle.describeResponse(response)
  }

  override fun cases() {
    func("resolveSessionIds") {
      case("nothing") { resolver.resolveSessionIds(WebOracle.request()) }
      case("header") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("X-Auth-Token" to "abc"))) }
      case("header name case") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("x-auth-token" to "abc"))) }
      case("empty header") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("X-Auth-Token" to ""))) }
      case("two headers") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("X-Auth-Token" to "a", "X-Auth-Token" to "b"))) }
      case("cookie") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=$b64one"))) }
      case("two cookies") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=$b64one; other=x; KOMGA-SESSION=$b64two"))) }
      case("cookie not base64") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=not*base64"))) }
      case("cookie plain value") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=abcd"))) }
      case("cookie of another name") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "SESSION=$b64one"))) }
      case("header wins over cookie") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("X-Auth-Token" to "h", "Cookie" to "KOMGA-SESSION=$b64one"))) }
      case("quoted cookie") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=\"$b64one\""))) }
    }
    func("setSessionId") {
      case("cookie") { set("session-1") }
      case("cookie over https") { set("session-1", scheme = "https") }
      case("cookie, same id already requested") { set("session-1", "Cookie" to "KOMGA-SESSION=$b64one") }
      case("header") { set("session-1", "X-Auth-Token" to "old") }
      case("header, same id") { set("session-1", "X-Auth-Token" to "session-1") }
      case("unicode id cookie") { set("sessiön-漫") }
    }
    func("expireSession") {
      case("cookie") { expire() }
      case("header") { expire("X-Auth-Token" to "abc") }
    }
    func("getResolver") {
      case("cookie resolver without header") { set("x").let { it[2] } }
      case("header resolver with header") { set("x", "X-Auth-Token" to "y").let { it[2] } }
    }
  }
}
