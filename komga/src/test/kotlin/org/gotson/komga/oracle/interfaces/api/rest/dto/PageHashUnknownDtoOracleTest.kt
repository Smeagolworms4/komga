package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.PageHashUnknown
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json

class PageHashUnknownDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      case("all fields") { PageHashUnknown("abc", 99, 4).toDto() }
      case("defaults") { PageHashUnknown("abc").toDto() }
      case("json") { json(PageHashUnknown("abc", 5000000000, 1).toDto()) }
    }
  }
}
