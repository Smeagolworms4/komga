package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class CollectionDtoOracleTest : OracleTest() {
  private val c =
    SeriesCollection(
      name = "Coll",
      ordered = true,
      seriesIds = listOf("S2", "S1", "S3"),
      id = "C1",
      createdDate = LocalDateTime.of(2020, 5, 5, 5, 5, 5),
      lastModifiedDate = LocalDateTime.of(2020, 11, 5, 5, 5, 5, 5000000),
      filtered = true,
    )

  override fun cases() {
    func("toDto") {
      case("all fields") { c.toDto() }
      case("empty") { c.copy(seriesIds = emptyList(), ordered = false, filtered = false).toDto() }
      case("json") { json(c.toDto()) }
    }
  }
}
