package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationToken
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.security.core.authority.SimpleGrantedAuthority

class ApiKeyAuthenticationTokenOracleTest : OracleTest() {
  private val authorities = listOf(SimpleGrantedAuthority("ROLE_B"), SimpleGrantedAuthority("ROLE_A"))

  override fun cases() {
    func("authenticated") {
      case("with authorities") { WebOracle.describeAuthentication(ApiKeyAuthenticationToken.authenticated("masked", "hashed", authorities)) }
      case("null authorities") { WebOracle.describeAuthentication(ApiKeyAuthenticationToken.authenticated("masked", "hashed", null)) }
      case("null principal and credentials") { WebOracle.describeAuthentication(ApiKeyAuthenticationToken.authenticated(null, null, emptyList())) }
      case("details") { ApiKeyAuthenticationToken.authenticated("p", "c", authorities).details }
      case("principal kept") { ApiKeyAuthenticationToken.authenticated(listOf(1, 2), "c", authorities).principal }
      case("erase credentials") { ApiKeyAuthenticationToken.authenticated("p", "c", authorities).apply { eraseCredentials() }.credentials }
      case("set unauthenticated") { ApiKeyAuthenticationToken.authenticated("p", "c", authorities).apply { isAuthenticated = false }.isAuthenticated }
    }
    func("unauthenticated") {
      case("token") { WebOracle.describeAuthentication(ApiKeyAuthenticationToken.unauthenticated("masked", "hashed")) }
      case("null values") { WebOracle.describeAuthentication(ApiKeyAuthenticationToken.unauthenticated(null, null)) }
      case("cannot be trusted") { ApiKeyAuthenticationToken.unauthenticated("p", "c").apply { isAuthenticated = true } }
      case("set false again") { ApiKeyAuthenticationToken.unauthenticated("p", "c").apply { isAuthenticated = false }.isAuthenticated }
    }
  }
}
