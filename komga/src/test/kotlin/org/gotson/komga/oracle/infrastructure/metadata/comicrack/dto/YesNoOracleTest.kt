package org.gotson.komga.oracle.infrastructure.metadata.comicrack.dto

import org.gotson.komga.infrastructure.metadata.comicrack.dto.YesNo
import org.gotson.komga.oracle.OracleTest

class YesNoOracleTest : OracleTest() {
  override fun cases() {
    func("fromValue") {
      for (v in listOf("Unknown", "No", "Yes", "", "yes", "YES", "no", " Yes", "Yes ", "Y", "N", "true", "1", "UNKNOWN")) case("'$v'") { YesNo.fromValue(v) }
    }
  }
}
