package org.gotson.komga.oracle.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.infrastructure.web.WebServerEffectiveSettings
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.web.server.WebServer
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent
import org.springframework.mock.web.MockServletContext

class WebServerEffectiveSettingsOracleTest : OracleTest() {
  private fun event(port: Int): ServletWebServerInitializedEvent {
    val server = mockk<WebServer>()
    every { server.port } returns port
    return ServletWebServerInitializedEvent(server, mockk<ServletWebServerApplicationContext>())
  }

  override fun cases() {
    func("onApplicationEvent") {
      val settings = WebServerEffectiveSettings(MockServletContext().apply { contextPath = "/komga" })
      case("before the event") { listOf(settings.effectiveServerPort, settings.effectiveServletContextPath) }
      case("after the event") {
        settings.onApplicationEvent(event(25600))
        listOf(settings.effectiveServerPort, settings.effectiveServletContextPath)
      }
      case("second event") {
        settings.onApplicationEvent(event(-1))
        settings.effectiveServerPort
      }
    }
  }
}
