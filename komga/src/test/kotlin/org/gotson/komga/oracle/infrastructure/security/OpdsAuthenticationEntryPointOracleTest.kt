package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.infrastructure.security.OpdsAuthenticationEntryPoint
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.springframework.security.authentication.BadCredentialsException

class OpdsAuthenticationEntryPointOracleTest : OracleTest() {
  private val services = InterfacesServices(OracleDb())
  private val entryPoint by lazy { OpdsAuthenticationEntryPoint(services.opdsGenerator, WebOracle.mapper) }

  private fun commence(
    host: String = "localhost",
    port: Int = 25600,
    scheme: String = "http",
    contextPath: String = "",
  ): List<Any?> {
    val request = WebOracle.request(uri = "$contextPath/opds/v2/catalog", host = host, port = port, scheme = scheme, contextPath = contextPath)
    val response = WebOracle.response()
    WebOracle.withRequest(request) { entryPoint.commence(request, response, BadCredentialsException("Bad credentials")) }
    return WebOracle.describeResponse(response)
  }

  override fun cases() {
    func("commence") {
      case("default") { commence() }
      case("https default port") { commence("komga.example.org", 443, "https") }
      case("context path") { commence(contextPath = "/komga") }
    }
  }
}
