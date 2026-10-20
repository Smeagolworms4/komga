package org.gotson.komga.oracle.infrastructure.session

import com.github.gotson.spring.session.caffeine.CaffeineIndexedSessionRepository
import org.gotson.komga.infrastructure.session.CaffeineSessionConfiguration
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.autoconfigure.session.SessionProperties
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import java.time.Duration

class CaffeineSessionConfigurationOracleTest : OracleTest() {
  private val config = CaffeineSessionConfiguration()

  private fun repository(
    sessionTimeout: Duration?,
    serverTimeout: Duration?,
  ): Any? {
    val caffeine = CaffeineIndexedSessionRepository()

    @Suppress("UNCHECKED_CAST")
    val repo = caffeine as FindByIndexNameSessionRepository<Session>
    val sessionProperties = SessionProperties()
    if (sessionTimeout != null) sessionProperties.timeout = sessionTimeout
    val serverProperties = ServerProperties()
    if (serverTimeout != null) serverProperties.servlet.session.timeout = serverTimeout
    config.customizeSessionRepository(sessionProperties, serverProperties).customize(caffeine)
    return repo.createSession().maxInactiveInterval.seconds
  }

  override fun cases() {
    func("customizeSessionRepository") {
      case("default timeout") { repository(null, null) }
      case("session timeout") { repository(Duration.ofDays(7), null) }
      case("server timeout") { repository(null, Duration.ofDays(1)) }
      case("session timeout over server timeout") { repository(Duration.ofSeconds(90), Duration.ofDays(1)) }
      case("sub-second") { repository(Duration.ofMillis(1500), null) }
    }
  }
}
