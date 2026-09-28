package org.gotson.komga.oracle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.util.UriUtils
import java.net.URLDecoder

/**
 * Web helpers for the oracle tests of the web layer (interfaces, infrastructure/web, infrastructure/security),
 * mirrored by test/unit/web-oracle.ts in KomgaJS: requests and responses without a servlet container, described
 * in a language-neutral way.
 */
object WebOracle {
  /** Spring Boot's ObjectMapper (same as [OracleDb.mapper]), without a database */
  val mapper: ObjectMapper by lazy {
    Jackson2ObjectMapperBuilder
      .json()
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
      .featuresToEnable(
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES,
        MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES,
      ).build()
  }

  /**
   * A request as Tomcat would build it: [uri] is the raw (encoded) request URI without query string, [query] the raw
   * query string, parsed into parameters; the Host header is set from [host] and [port]. [uri] includes [contextPath].
   */
  fun request(
    method: String = "GET",
    uri: String = "/",
    query: String? = null,
    headers: List<Pair<String, String>> = emptyList(),
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    remoteAddr: String = "127.0.0.1",
    contextPath: String = "",
  ): MockHttpServletRequest =
    MockHttpServletRequest(method, uri).apply {
      this.scheme = scheme
      this.isSecure = scheme == "https"
      this.serverName = host
      this.serverPort = port
      this.remoteAddr = remoteAddr
      this.queryString = query
      this.contextPath = contextPath
      this.servletPath = UriUtils.decode(uri, Charsets.UTF_8).removePrefix(contextPath)
      val default = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
      addHeader("Host", if (default) host else "$host:$port")
      headers.forEach { (k, v) ->
        // like Tomcat, the Cookie header is parsed into cookies
        if (k.equals("Cookie", ignoreCase = true)) {
          val cookies =
            v.split(';').map { it.trim() }.filter { it.contains('=') }.map {
              val value = it.substringAfter('=')
              jakarta.servlet.http.Cookie(it.substringBefore('='), value.removeSurrounding("\""))
            }
          setCookies(*((this.cookies ?: emptyArray<jakarta.servlet.http.Cookie>()) + cookies.toTypedArray()))
        } else {
          addHeader(k, v)
        }
      }
      query?.split('&')?.filter { it.isNotEmpty() }?.forEach {
        val i = it.indexOf('=')
        val k = if (i < 0) it else it.substring(0, i)
        val v = if (i < 0) "" else it.substring(i + 1)
        addParameter(URLDecoder.decode(k, Charsets.UTF_8), URLDecoder.decode(v, Charsets.UTF_8))
      }
    }

  /** response keeping the Set-Cookie headers as written (MockHttpServletResponse re-formats them), like Tomcat */
  class OracleResponse : MockHttpServletResponse() {
    val rawSetCookies = mutableListOf<String>()

    override fun addHeader(
      name: String,
      value: String?,
    ) {
      if (name.equals("Set-Cookie", ignoreCase = true) && value != null) rawSetCookies.add(value) else super.addHeader(name, value)
    }
  }

  fun response(): MockHttpServletResponse = OracleResponse()

  /** status, error message, headers (lower-cased names, sorted) and body of a response */
  fun describeResponse(response: HttpServletResponse): List<Any?> {
    val r = response as MockHttpServletResponse
    return listOf(
      r.status,
      r.errorMessage,
      (
        r.headerNames.filterNot { it.equals("Set-Cookie", ignoreCase = true) }.map { listOf(it.lowercase(), r.getHeaders(it).map { v -> stableHttpDate(v) }) } +
          ((r as? OracleResponse)?.rawSetCookies?.takeIf { it.isNotEmpty() }?.let { listOf(listOf("set-cookie", it.toList())) } ?: emptyList())
      ).sortedBy { it[0] as String },
      r.getContentAsString(Charsets.UTF_8),
    )
  }

  /** status, headers (lower-cased names, sorted) and body of a response entity (body left as is) */
  fun describeEntity(entity: ResponseEntity<*>): List<Any?> =
    listOf(
      entity.statusCode.value(),
      entity.headers.map { (k, v) -> listOf(k.lowercase(), v.map { stableHttpDate(it) }) }.sortedBy { it[0] as String },
      entity.body,
    )

  /** Runs [block] with [request] bound to the current thread (RequestContextHolder), like the DispatcherServlet */
  fun <T> withRequest(
    request: HttpServletRequest,
    block: () -> T,
  ): T {
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request, response()))
    try {
      return block()
    } finally {
      RequestContextHolder.resetRequestAttributes()
    }
  }

  /** class, name, credentials, authenticated flag and authorities of an authentication */
  fun describeAuthentication(authentication: Authentication?): List<Any?>? =
    authentication?.let {
      listOf(
        it::class.java.simpleName,
        it.name,
        it.credentials?.toString(),
        it.isAuthenticated,
        it.authorities.map { a -> a.authority }.sorted(),
      )
    }

  /** an HTTP date (RFC 1123) within one day of now becomes `@now` (Last-Modified of rows dated by the database) */
  fun stableHttpDate(v: String): String =
    try {
      val t = java.time.ZonedDateTime.parse(v, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
      if (java.time.Duration.between(t, java.time.Instant.now()).abs() < java.time.Duration.ofDays(1)) "@now" else v
    } catch (_: Exception) {
      v
    }

  private val NOW_TIME = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?""")

  /**
   * Replaces in a serialized document the ISO date-times within one day of today (generated by `now()`) by `@now`,
   * like [Canon.stable] does for canonical dates.
   */
  fun stableText(s: String): String {
    val today = java.time.LocalDate.now()
    val near = listOf(today.minusDays(1), today, today.plusDays(1)).map { it.toString() }.toSet()
    return NOW_TIME.replace(s) { if (it.value.substring(0, 10) in near) "@now" else it.value }
  }
}
