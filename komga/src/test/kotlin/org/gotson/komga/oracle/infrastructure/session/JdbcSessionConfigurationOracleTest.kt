package org.gotson.komga.oracle.infrastructure.session

import org.springframework.session.SessionRepository
import org.springframework.session.Session
import org.gotson.komga.infrastructure.session.JdbcSessionConfiguration
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.autoconfigure.session.SessionProperties
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.springframework.transaction.support.TransactionOperations
import java.time.Duration

class JdbcSessionConfigurationOracleTest : OracleTest() {
  private val config = JdbcSessionConfiguration()

  private fun repository(
    sessionTimeout: Duration?,
    serverTimeout: Duration?,
  ): Any? {
    val repo = JdbcIndexedSessionRepository(JdbcTemplate(), TransactionOperations.withoutTransaction())
    val sessionProperties = SessionProperties()
    if (sessionTimeout != null) sessionProperties.timeout = sessionTimeout
    val serverProperties = ServerProperties()
    if (serverTimeout != null) serverProperties.servlet.session.timeout = serverTimeout
    config.customizeSessionRepository(sessionProperties, serverProperties).customize(repo)

    @Suppress("UNCHECKED_CAST")
    return (repo as SessionRepository<Session>).createSession().maxInactiveInterval.seconds
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
