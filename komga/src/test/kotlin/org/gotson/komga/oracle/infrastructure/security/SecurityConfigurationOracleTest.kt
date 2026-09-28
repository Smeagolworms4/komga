package org.gotson.komga.oracle.infrastructure.security

import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository
import io.mockk.mockk
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.KomgaUserDetailsService
import org.gotson.komga.infrastructure.security.OpdsAuthenticationEntryPoint
import org.gotson.komga.infrastructure.security.SecurityConfiguration
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationProvider
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationToken
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.infrastructure.security.apikey.ApiKeySupport
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.springframework.security.authentication.AuthenticationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.session.security.SpringSessionBackedSessionRegistry

class SecurityConfigurationOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val events = mutableListOf<List<Any?>>()
  private val publisher =
    object : AuthenticationEventPublisher {
      override fun publishAuthenticationSuccess(authentication: Authentication) {
        events.add(listOf("success", authentication::class.java.simpleName, authentication.name))
      }

      override fun publishAuthenticationFailure(
        exception: AuthenticationException,
        authentication: Authentication,
      ) {
        events.add(listOf("failure", exception::class.java.simpleName, exception.message, authentication::class.java.simpleName))
      }
    }
  private val config by lazy {
    SecurityConfiguration(
      services.settings,
      KomgaUserDetailsService(db.komgaUserDao),
      ApiKeyAuthenticationProvider(db.komgaUserDao),
      mockk(),
      mockk(),
      "KOMGA-SESSION",
      ApiKeySupport.detailsSource,
      SpringSessionBackedSessionRegistry(CaffeineIndexedSessionRepository()),
      OpdsAuthenticationEntryPoint(services.opdsGenerator, WebOracle.mapper),
      publisher,
      ApiKeySupport.tokenEncoder,
      ApiKeySupport.hasher,
      null,
    )
  }

  private fun run(
    filter: Filter,
    uri: String,
    vararg headers: Pair<String, String>,
  ): List<Any?> {
    SecurityContextHolder.clearContext()
    val response = WebOracle.response()
    val seen = mutableListOf<Any?>()
    try {
      filter.doFilter(
        WebOracle.request(uri = uri, headers = headers.toList()),
        response,
        FilterChain { _, _ ->
          seen.add(
            SecurityContextHolder.getContext().authentication?.let {
              WebOracle.describeAuthentication(it)!! + listOf((it.principal as? KomgaPrincipal)?.apiKey?.id)
            },
          )
        },
      )
      return listOf(seen, response.status, events.toList().also { events.clear() })
    } finally {
      SecurityContextHolder.clearContext()
    }
  }

  override fun cases() {
    func("koboAuthenticationFilter") {
      case("populate") { ApiKeySupport.populate(db) }
      case("class") { config.koboAuthenticationFilter()::class.java.simpleName }
      case("valid key in path") { run(config.koboAuthenticationFilter(), "/kobo/key-one/v1/library/sync") }
      case("invalid key in path") { run(config.koboAuthenticationFilter(), "/kobo/wrong/v1/library/sync") }
      case("header is ignored") { run(config.koboAuthenticationFilter(), "/api/v1/books", "X-API-Key" to "key-one") }
    }
    func("kosyncAuthenticationFilter") {
      case("valid key in header") { run(config.kosyncAuthenticationFilter(), "/koreader/syncs/progress", "X-Auth-User" to "key-two") }
      case("invalid key") { run(config.kosyncAuthenticationFilter(), "/koreader/syncs/progress", "X-Auth-User" to "nope") }
      case("no header") { run(config.kosyncAuthenticationFilter(), "/koreader/syncs/progress") }
    }
    func("restAuthenticationFilter") {
      case("valid key") { run(config.restAuthenticationFilter(), "/api/v1/books", "X-API-Key" to "admin-key") }
      case("invalid key") { run(config.restAuthenticationFilter(), "/api/v1/books", "X-API-Key" to "nope") }
      case("other header") { run(config.restAuthenticationFilter(), "/api/v1/books", "X-Auth-User" to "admin-key") }
    }
    func("apiKeyAuthenticationProvider") {
      case("authenticate") {
        val m = config.apiKeyAuthenticationProvider()
        val auth = m.authenticate(ApiKeyAuthenticationToken.unauthenticated(ApiKeySupport.hasher.computeHash("key-one"), ApiKeySupport.tokenEncoder.encode("key-one")))
        listOf(m::class.java.simpleName, WebOracle.describeAuthentication(auth), events.toList().also { events.clear() })
      }
      case("failure") {
        try {
          config.apiKeyAuthenticationProvider().authenticate(ApiKeyAuthenticationToken.unauthenticated("x", "y"))
        } catch (e: Exception) {
          listOf(e::class.java.simpleName, e.message, events.toList().also { events.clear() })
        }
      }
    }
  }
}
