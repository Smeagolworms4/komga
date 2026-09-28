package org.gotson.komga.oracle.infrastructure.security.session

import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository
import org.gotson.komga.infrastructure.security.session.SessionConfiguration
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.session.web.http.CookieSerializer
import java.time.Duration

class SessionConfigurationOracleTest : OracleTest() {
  private val config = SessionConfiguration()

  private fun writeCookie(
    serializer: CookieSerializer,
    value: String,
  ): List<Any?> {
    val response = WebOracle.response()
    serializer.writeCookieValue(CookieSerializer.CookieValue(WebOracle.request(uri = "/api/v1/users/me"), response, value))
    return WebOracle.describeResponse(response)
  }

  private fun repository(timeout: Duration?): Any? {
    val caffeine = CaffeineIndexedSessionRepository()
    @Suppress("UNCHECKED_CAST")
    val repo = caffeine as FindByIndexNameSessionRepository<Session>
    val serverProperties = ServerProperties()
    if (timeout != null) serverProperties.servlet.session.timeout = timeout
    config.customizeSessionRepository(serverProperties).customize(caffeine)
    return repo.createSession().maxInactiveInterval.seconds
  }

  override fun cases() {
    func("sessionCookieName") {
      case("name") { config.sessionCookieName() }
    }
    func("sessionHeaderName") {
      case("name") { config.sessionHeaderName() }
    }
    func("cookieSerializer") {
      case("write cookie") { writeCookie(config.cookieSerializer("KOMGA-SESSION"), "session-1") }
      case("write empty cookie") { writeCookie(config.cookieSerializer("KOMGA-SESSION"), "") }
      case("other name") { writeCookie(config.cookieSerializer("OTHER"), "v") }
      case("read cookie") { config.cookieSerializer("KOMGA-SESSION").readCookieValues(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=c2Vzc2lvbi0x"))) }
    }
    func("httpSessionIdResolver") {
      val resolver = config.httpSessionIdResolver("X-Auth-Token", config.cookieSerializer("KOMGA-SESSION"))
      case("class") { resolver::class.java.simpleName }
      case("header") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("X-Auth-Token" to "abc"))) }
      case("cookie") { resolver.resolveSessionIds(WebOracle.request(headers = listOf("Cookie" to "KOMGA-SESSION=c2Vzc2lvbi0x"))) }
    }
    func("customizeSessionRepository") {
      case("default timeout") { repository(null) }
      case("1 day") { repository(Duration.ofDays(1)) }
      case("90 seconds") { repository(Duration.ofSeconds(90)) }
      case("sub-second") { repository(Duration.ofMillis(1500)) }
    }
    func("sessionRegistry") {
      val caffeine = CaffeineIndexedSessionRepository().apply { init() }

      @Suppress("UNCHECKED_CAST")
      val repo = caffeine as FindByIndexNameSessionRepository<Session>
      val registry = config.sessionRegistry(caffeine)
      case("class") { registry::class.java.simpleName }
      case("sessions of a principal") {
        repo.save(repo.createSession().apply { setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "user@example.org") })
        repo.save(repo.createSession().apply { setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "user@example.org") })
        repo.save(repo.createSession().apply { setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "other@example.org") })
        listOf(registry.getAllSessions("user@example.org", false).size, registry.getAllSessions("other@example.org", true).size, registry.getAllSessions("nobody", true).size)
      }
      case("session information") {
        val s = repo.createSession().apply { setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "x@example.org") }
        repo.save(s)
        registry.getSessionInformation(s.id)?.let { listOf(it.principal, it.sessionId == s.id, it.isExpired) }
      }
      case("expire now") {
        val s = repo.createSession().apply { setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "y@example.org") }
        repo.save(s)
        registry.getSessionInformation(s.id)!!.expireNow()
        listOf(registry.getAllSessions("y@example.org", false).size, registry.getAllSessions("y@example.org", true).size, registry.getSessionInformation(s.id)!!.isExpired)
      }
      case("unknown session") { registry.getSessionInformation("unknown") }
      case("security context principal") {
        val s = repo.createSession().apply { setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextImpl()) }
        repo.save(s)
        registry.getSessionInformation(s.id)?.principal
      }
      case("indexed by security context") {
        val s = repo.createSession().apply { setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("ctx@example.org", null, emptyList()))) }
        repo.save(s)
        listOf(registry.getAllSessions("ctx@example.org", false).map { it.principal }, registry.getSessionInformation(s.id)?.principal)
      }
      case("all principals") { registry.allPrincipals }
    }
  }
}
