package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class PageHashKnownDtoOracleTest : OracleTest() {
  private val p =
    PageHashKnown(
      hash = "abc",
      size = 1234,
      action = PageHashKnown.Action.DELETE_AUTO,
      deleteCount = 3,
      matchCount = 7,
      createdDate = LocalDateTime.of(2021, 8, 1, 12, 0),
      lastModifiedDate = LocalDateTime.of(2021, 12, 1, 12, 0),
    )

  override fun cases() {
    func("toDto") {
      case("all fields") { p.toDto() }
      case("null size, ignore") {
        PageHashKnown("h", null, PageHashKnown.Action.IGNORE, createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)).toDto()
      }
      case("json") { json(p.toDto()) }
    }
  }
}
