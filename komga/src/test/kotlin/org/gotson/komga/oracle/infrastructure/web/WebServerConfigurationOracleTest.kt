package org.gotson.komga.oracle.infrastructure.web

import io.mockk.mockk
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.web.WebServerConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory

class WebServerConfigurationOracleTest : OracleTest() {
  private val db = OracleDb()
  private val settings = KomgaSettingsProvider(db.serverSettingsDao, mockk(relaxed = true))

  private fun customize(
    port: Int?,
    contextPath: String?,
  ): List<Any?> {
    settings.serverPort = port
    settings.serverContextPath = contextPath
    val factory = TomcatServletWebServerFactory()
    WebServerConfiguration(settings).customize(factory)
    return listOf(factory.port, factory.contextPath)
  }

  override fun cases() {
    func("customize") {
      case("nothing set") { customize(null, null) }
      case("port 25600") { customize(25600, null) }
      case("port 2") { customize(2, null) }
      case("port 1 ignored") { customize(1, null) }
      case("port 0 ignored") { customize(0, null) }
      case("negative port ignored") { customize(-8080, null) }
      case("context path") { customize(null, "/komga") }
      case("nested context path") { customize(null, "/a/b") }
      case("context path without leading slash ignored") { customize(null, "komga") }
      case("context path with trailing slash ignored") { customize(null, "/komga/") }
      case("root context path ignored") { customize(null, "/") }
      case("empty context path ignored") { customize(null, "") }
      case("both") { customize(8081, "/k") }
    }
  }
}
