package org.gotson.komga.oracle.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.web.KoboMissingPortFilterConfiguration
import org.gotson.komga.infrastructure.web.WebServerEffectiveSettings
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered
import org.springframework.mock.web.MockServletContext
import org.springframework.web.filter.ForwardedHeaderFilter

class KoboMissingPortFilterConfigurationOracleTest : OracleTest() {
  private val db = OracleDb()
  private val settings = KomgaSettingsProvider(db.serverSettingsDao, mockk(relaxed = true))
  private val serverSettings = WebServerEffectiveSettings(MockServletContext())
  private val forwarded = FilterRegistrationBean(ForwardedHeaderFilter()).apply { order = Ordered.HIGHEST_PRECEDENCE }

  private fun port(): Any? {
    val bean = KoboMissingPortFilterConfiguration(settings, serverSettings, null).koboMissingPortFilter()
    val seen = mutableListOf<Int>()
    bean.filter.doFilter(WebOracle.request(uri = "/kobo/k", port = 8080), WebOracle.response(), FilterChain { req, _ -> seen.add((req as HttpServletRequest).serverPort) })
    return seen
  }

  override fun cases() {
    func("koboMissingPortFilter") {
      val bean = KoboMissingPortFilterConfiguration(settings, serverSettings, null).koboMissingPortFilter()
      case("url patterns") { bean.urlPatterns.toList() }
      case("name") { bean.filterName }
      case("order") { bean.order }
      case("filter class") { bean.filter::class.java.simpleName }
      case("no kobo port, no effective port") { port() }
      case("effective port") {
        serverSettings.effectiveServerPort = 25600
        port()
      }
      case("kobo port wins") {
        settings.koboPort = 443
        port()
      }
      case("kobo port removed") {
        settings.koboPort = null
        port()
      }
    }
    func("adjustForwardHeaderFilterOrder") {
      case("forwarded header filter moved after") {
        KoboMissingPortFilterConfiguration(settings, serverSettings, forwarded).adjustForwardHeaderFilterOrder()
        forwarded.order
      }
      case("no forwarded header filter") { KoboMissingPortFilterConfiguration(settings, serverSettings, null).adjustForwardHeaderFilterOrder() }
    }
  }
}
