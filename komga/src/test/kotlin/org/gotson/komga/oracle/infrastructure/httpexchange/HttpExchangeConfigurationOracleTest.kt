package org.gotson.komga.oracle.infrastructure.httpexchange

import org.gotson.komga.infrastructure.httpexchange.HttpExchangeConfiguration
import org.gotson.komga.oracle.OracleTest

class HttpExchangeConfigurationOracleTest : OracleTest() {
  override fun cases() {
    val config = HttpExchangeConfiguration()
    func("httpExchangeRepository") {
      case("type") { config.httpExchangeRepository()::class.simpleName }
      case("empty") { config.httpExchangeRepository().findAll() }
      case("same instance") { config.httpExchangeRepository() === config.httpExchangeRepository() }
      case("new configuration, new repository") { HttpExchangeConfiguration().httpExchangeRepository() === config.httpExchangeRepository() }
    }
  }
}
