package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Author
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json

class AuthorDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      case("normalized by Author") { Author("  John DOE ", " Writer ").toDto() }
      case("empty") { Author("", "").toDto() }
      case("json") { json(Author("Ünïcode", "PENCILLER").toDto()) }
    }
  }
}
