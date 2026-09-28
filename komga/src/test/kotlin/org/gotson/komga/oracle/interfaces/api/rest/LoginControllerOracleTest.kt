package org.gotson.komga.oracle.interfaces.api.rest

import jakarta.servlet.http.HttpServletRequest
import org.gotson.komga.interfaces.api.rest.LoginController
import org.gotson.komga.oracle.OracleTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.session.web.http.CookieSerializer

class LoginControllerOracleTest : OracleTest() {
  private val calls = RestOracle.Calls()

  /** Records the cookie values written (same fake in the TypeScript twin) */
  private val serializer =
    object : CookieSerializer {
      override fun writeCookieValue(cookieValue: CookieSerializer.CookieValue) {
        calls.add("writeCookieValue", cookieValue.cookieValue, cookieValue.cookieMaxAge)
      }

      override fun readCookieValues(request: HttpServletRequest): List<String> = emptyList()
    }
  private val controller = LoginController(serializer)

  override fun cases() {
    func("convertHeaderSessionToCookie") {
      case("session id") {
        controller.convertHeaderSessionToCookie(MockHttpServletRequest(), MockHttpServletResponse(), MockHttpSession(null, "SESSION-1"))
        calls.take()
      }
      case("other session") {
        listOf(controller.convertHeaderSessionToCookie(MockHttpServletRequest(), MockHttpServletResponse(), MockHttpSession(null, "abc-def")), calls.take())
      }
    }
  }
}
