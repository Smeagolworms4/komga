package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.AlternateTitle
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json

class AlternateTitleDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      case("simple") { AlternateTitle("en", "Title").toDto() }
      case("empty") { AlternateTitle("", "").toDto() }
      case("unicode json") { json(AlternateTitle("日本語", "タイトル \"q\"").toDto()) }
    }
  }
}
