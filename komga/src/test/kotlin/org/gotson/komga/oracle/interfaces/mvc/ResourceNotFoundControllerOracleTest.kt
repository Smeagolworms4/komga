package org.gotson.komga.oracle.interfaces.mvc

import org.gotson.komga.interfaces.mvc.ResourceNotFoundController
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class ResourceNotFoundControllerOracleTest : OracleTest() {
  private val controller = ResourceNotFoundController()

  override fun cases() {
    func("notFound") {
      case("apis") { controller.apis }
      listOf(
        "/",
        "/books/123",
        "/api",
        "/api/v1/unknown",
        "/API/v1/x",
        "/apiv2",
        "/opds/v1.2/x",
        "/OPDS",
        "/sse/v1/events",
        "/sSe",
        "/kobo/x",
        "/web/api",
        "/%61pi/x",
      ).forEach { uri -> case(uri) { controller.notFound(WebOracle.request(uri = uri)) } }
    }
  }
}
