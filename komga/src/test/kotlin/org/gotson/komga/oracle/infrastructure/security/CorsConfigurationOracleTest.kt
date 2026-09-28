package org.gotson.komga.oracle.infrastructure.security

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.infrastructure.security.CorsConfiguration
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.http.HttpMethod
import org.springframework.mock.env.MockEnvironment

class CorsConfigurationOracleTest : OracleTest() {
  private fun source(vararg origins: String) =
    CorsConfiguration().corsConfigurationSource("X-Auth-Token", KomgaProperties().apply { cors.allowedOrigins = origins.toList() })

  private fun outcome(vararg properties: Pair<String, String>): List<Any?> {
    val env = MockEnvironment()
    properties.forEach { (k, v) -> env.setProperty(k, v) }
    val context = mockk<ConditionContext>()
    every { context.environment } returns env
    val o = CorsConfiguration.CorsAllowedOriginsPresent().getMatchOutcome(context, mockk<AnnotatedTypeMetadata>())
    return listOf(o.isMatch, o.message)
  }

  override fun cases() {
    func("corsConfigurationSource") {
      case("configuration") {
        source("http://localhost:8081", "https://komga.example.org/").getCorsConfiguration(WebOracle.request(uri = "/api/v1/books"))?.let {
          listOf(it.allowedOrigins, it.allowedOriginPatterns, it.allowedMethods, it.allowedHeaders, it.exposedHeaders, it.allowCredentials, it.maxAge)
        }
      }
      case("all paths") { listOf("/", "/api/v1/books", "/opds/v2/catalog", "/kobo/x/v1/library/sync").map { source("http://a").getCorsConfiguration(WebOracle.request(uri = it)) != null } }
      case("check origin") {
        val c = source("http://localhost:8081", "https://komga.example.org/").getCorsConfiguration(WebOracle.request(uri = "/api"))!!
        listOf("http://localhost:8081", "https://komga.example.org", "https://KOMGA.example.org", "http://localhost:8082", "null", "").map { c.checkOrigin(it) }
      }
      case("check method") {
        val c = source("http://a").getCorsConfiguration(WebOracle.request(uri = "/api"))!!
        listOf("GET", "POST", "PATCH", "DELETE", "TRACE", "CONNECT").map { c.checkHttpMethod(HttpMethod.valueOf(it))?.map { m -> m.name() } }
      }
      case("check headers") {
        val c = source("http://a").getCorsConfiguration(WebOracle.request(uri = "/api"))!!
        listOf(listOf("X-Auth-Token"), listOf("Content-Type", "Authorization"), emptyList()).map { c.checkHeaders(it) }
      }
      case("no origin") { source().getCorsConfiguration(WebOracle.request(uri = "/api"))?.allowedOrigins }
    }
    func("getMatchOutcome") {
      case("no property") { outcome() }
      case("one origin") { outcome("komga.cors.allowed-origins" to "http://localhost:8081") }
      case("comma separated") { outcome("komga.cors.allowed-origins" to "http://a,http://b") }
      case("empty") { outcome("komga.cors.allowed-origins" to "") }
      case("indexed") { outcome("komga.cors.allowed-origins[0]" to "http://a") }
      case("camel case") { outcome("komga.cors.allowedOrigins" to "http://a") }
      case("other property") { outcome("komga.cors.other" to "x") }
    }
  }
}
