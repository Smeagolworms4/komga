package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.infrastructure.security.UserAgentWebAuthenticationDetailsSource
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class UserAgentWebAuthenticationDetailsSourceOracleTest : OracleTest() {
  private val source = UserAgentWebAuthenticationDetailsSource()

  private fun build(
    headers: List<Pair<String, String>> = emptyList(),
    remoteAddr: String = "127.0.0.1",
  ) = source.buildDetails(WebOracle.request(headers = headers, remoteAddr = remoteAddr)).let { listOf(it::class.java.simpleName, it.remoteAddress, it.sessionId, it.userAgent) }

  override fun cases() {
    func("buildDetails") {
      case("no user agent") { build() }
      case("user agent") { build(listOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64)")) }
      case("lower-case header") { build(listOf("user-agent" to "curl/8.0")) }
      case("empty user agent") { build(listOf("User-Agent" to "")) }
      case("two user agents") { build(listOf("User-Agent" to "a", "User-Agent" to "b")) }
      case("ipv6") { build(remoteAddr = "0:0:0:0:0:0:0:1") }
      case("forwarded for is ignored") { build(listOf("X-Forwarded-For" to "1.2.3.4"), "10.0.0.1") }
      case("unicode user agent") { build(listOf("User-Agent" to "Kobo ünï")) }
    }
  }
}
