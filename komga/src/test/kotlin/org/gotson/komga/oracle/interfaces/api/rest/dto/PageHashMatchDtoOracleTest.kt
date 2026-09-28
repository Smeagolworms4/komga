package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.PageHashMatch
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.net.URL

class PageHashMatchDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      case("simple") { PageHashMatch("B1", URL("file:/lib/series/book.cbz"), 3, "p3.jpg", 1000, "image/jpeg").toDto() }
      case("encoded url") { PageHashMatch("B1", URL("file:/lib/my%20series/%C3%A9.cbz"), 1, "", 0, "").toDto() }
      case("json") { json(PageHashMatch("B1", URL("file:/a/b.cbz"), 1, "x.png", 5, "image/png").toDto()) }
    }
  }
}
