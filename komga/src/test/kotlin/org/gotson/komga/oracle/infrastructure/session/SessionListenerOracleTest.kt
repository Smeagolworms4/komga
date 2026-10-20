package org.gotson.komga.oracle.infrastructure.session

import org.gotson.komga.infrastructure.session.SessionListener
import org.gotson.komga.oracle.OracleTest
import org.springframework.session.MapSession
import org.springframework.session.events.SessionCreatedEvent
import org.springframework.session.events.SessionDeletedEvent
import org.springframework.session.events.SessionExpiredEvent

class SessionListenerOracleTest : OracleTest() {
  override fun cases() {
    func("sessionEventLogging") {
      val listener = SessionListener()
      case("created") { listener.sessionEventLogging(SessionCreatedEvent(this, MapSession("S1"))) }
      case("deleted") { listener.sessionEventLogging(SessionDeletedEvent(this, MapSession("S2"))) }
      case("expired") { listener.sessionEventLogging(SessionExpiredEvent(this, MapSession("S3"))) }
    }
  }
}
