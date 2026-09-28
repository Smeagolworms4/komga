package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class ReadListDtoOracleTest : OracleTest() {
  private val rl =
    ReadList(
      name = "RL",
      summary = "sum",
      ordered = false,
      bookIds = sortedMapOf(3 to "B3", 1 to "B1", 2 to "B2"),
      id = "R1",
      createdDate = LocalDateTime.of(2020, 5, 5, 5, 5, 5),
      lastModifiedDate = LocalDateTime.of(2020, 11, 5, 5, 5, 5),
      filtered = true,
    )

  override fun cases() {
    func("toDto") {
      case("book ids in key order") { rl.toDto() }
      case("empty") { rl.copy(bookIds = sortedMapOf()).toDto() }
      case("sparse keys") { rl.copy(bookIds = sortedMapOf(10 to "A", -1 to "Z", 5 to "M")).toDto() }
      case("json") { json(rl.toDto()) }
    }
  }
}
