package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.net.URI

class WebLinkDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      case("https") { WebLink("site", URI("https://example.org/a?b=c#d")).toDto() }
      case("encoded") { WebLink("enc", URI("https://example.org/a%20b/%C3%A9")).toDto() }
      case("relative") { WebLink("", URI("relative/path")).toDto() }
      case("json") { json(WebLink("x", URI("http://h/")).toDto()) }
    }
  }
}
