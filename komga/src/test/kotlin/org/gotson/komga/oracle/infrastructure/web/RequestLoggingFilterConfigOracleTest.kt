package org.gotson.komga.oracle.infrastructure.web

import jakarta.servlet.http.HttpServletRequest
import org.gotson.komga.infrastructure.web.RequestLoggingFilterConfig
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.web.filter.AbstractRequestLoggingFilter

class RequestLoggingFilterConfigOracleTest : OracleTest() {
  private val filter = RequestLoggingFilterConfig().logFilter()

  private fun field(name: String): Any? {
    val f = AbstractRequestLoggingFilter::class.java.getDeclaredField(name)
    f.isAccessible = true
    return f.get(filter)
  }

  private fun message(request: HttpServletRequest): String {
    val m = AbstractRequestLoggingFilter::class.java.getDeclaredMethod("createMessage", HttpServletRequest::class.java, String::class.java, String::class.java)
    m.isAccessible = true
    return m.invoke(filter, request, field("afterMessagePrefix"), field("afterMessageSuffix")) as String
  }

  override fun cases() {
    func("logFilter") {
      case("settings") {
        listOf("includeQueryString", "includePayload", "maxPayloadLength", "includeHeaders", "includeClientInfo", "beforeMessagePrefix", "afterMessagePrefix", "afterMessageSuffix").map { listOf(it, field(it)) }
      }
      case("message") { message(WebOracle.request(uri = "/api/v1/books")) }
      case("message with query") { message(WebOracle.request(uri = "/api/v1/books", query = "page=1&sort=a,b")) }
    }
  }
}
