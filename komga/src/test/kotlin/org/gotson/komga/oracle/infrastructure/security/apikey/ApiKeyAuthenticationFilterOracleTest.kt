package org.gotson.komga.oracle.infrastructure.security.apikey

import jakarta.servlet.FilterChain
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationFilter
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationProvider
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationToken
import org.gotson.komga.infrastructure.security.apikey.HeaderApiKeyAuthenticationConverter
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository

class ApiKeyAuthenticationFilterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val filter =
    ApiKeyAuthenticationFilter(
      ProviderManager(ApiKeyAuthenticationProvider(db.komgaUserDao)),
      HeaderApiKeyAuthenticationConverter("X-API-Key", ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource),
    )

  private fun describe(a: Authentication?): List<Any?>? =
    a?.let { WebOracle.describeAuthentication(it)!! + listOf((it.principal as? KomgaPrincipal)?.apiKey?.id) }

  private val roles = listOf(SimpleGrantedAuthority("ROLE_USER"))

  private fun masked(key: String) = ApiKeySupport.hasher.computeHash(key)

  private fun run(
    key: String?,
    existing: Authentication? = null,
  ): List<Any?> {
    SecurityContextHolder.clearContext()
    if (existing != null) SecurityContextHolder.getContext().authentication = existing
    val request = WebOracle.request(uri = "/api/v1/books", headers = listOfNotNull(key?.let { "X-API-Key" to it }))
    val response = WebOracle.response()
    val seen = mutableListOf<Any?>()
    try {
      filter.doFilter(request, response, FilterChain { _, _ -> seen.add(describe(SecurityContextHolder.getContext().authentication)) })
      val saved = request.getAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME) as SecurityContext?
      return listOf(seen, saved?.let { describe(it.authentication) }, response.status)
    } finally {
      SecurityContextHolder.clearContext()
    }
  }

  override fun cases() {
    func("doFilterInternal") {
      case("populate") { ApiKeySupport.populate(db) }
      case("no key") { run(null) }
      case("valid key") { run("key-one") }
      case("valid admin key") { run("admin-key") }
      case("invalid key") { run("wrong") }
      case("empty key") { run("") }
    }
    func("unsuccessfulAuthentication") {
      case("clears an existing authentication") { run("wrong", UsernamePasswordAuthenticationToken.authenticated("someone", null, roles)) }
    }
    func("successfulAuthentication") {
      case("replaces an existing authentication of another user") { run("key-two", UsernamePasswordAuthenticationToken.authenticated("someone", null, roles)) }
    }
    func("authenticationIsRequired") {
      case("same api key authentication already present") { run("key-one", ApiKeyAuthenticationToken.authenticated(masked("key-one"), null, roles)) }
      case("same name but not authenticated") { run("key-one", ApiKeyAuthenticationToken.unauthenticated(masked("key-one"), null)) }
      case("same name, password authentication") { run("key-one", UsernamePasswordAuthenticationToken.authenticated(masked("key-one"), null, roles)) }
      case("same name, anonymous") { run("key-one", AnonymousAuthenticationToken("k", masked("key-one"), roles)) }
      case("other name, api key authentication") { run("key-one", ApiKeyAuthenticationToken.authenticated("other", null, roles)) }
      case("invalid key with same api key authentication present") { run("wrong", ApiKeyAuthenticationToken.authenticated(masked("wrong"), null, roles)) }
    }
  }
}
