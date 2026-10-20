package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.unpackFilters
import org.gotson.komga.oracle.OracleTest

class FilterByOracleTest : OracleTest() {
  override fun cases() {
    func("unpackFilters") {
      case("nothing") { unpackFilters() }
      case("library") { unpackFilters(libraryIds = setOf("L1", "L2")) }
      case("collection") { unpackFilters(collectionIds = setOf("C1")) }
      case("series") { unpackFilters(seriesIds = setOf("S1")) }
      case("read list") { unpackFilters(readListIds = setOf("R1")) }
      case("library wins") { unpackFilters(setOf("L1"), setOf("C1"), setOf("S1"), setOf("R1")) }
      case("collection over series") { unpackFilters(emptySet(), setOf("C1"), setOf("S1"), setOf("R1")) }
      case("series over read list") { unpackFilters(seriesIds = setOf("S1"), readListIds = setOf("R1")) }
      case("all empty") { unpackFilters(emptySet(), emptySet(), emptySet(), emptySet()) }
    }
  }
}
