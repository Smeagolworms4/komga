package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.infrastructure.security.apikey.ApiKeyGenerator
import org.gotson.komga.oracle.OracleTest

class ApiKeyGeneratorOracleTest : OracleTest() {
  override fun cases() {
    func("generate") {
      val keys = (1..20).map { ApiKeyGenerator().generate() }
      case("length") { keys.map { it.length }.toSet() }
      case("lower-case hexadecimal") { keys.all { k -> k.all { it in '0'..'9' || it in 'a'..'f' } } }
      case("uuid v4 version digit") { keys.map { it[12] }.toSet().map { it.toString() } }
      case("uuid v4 variant digit") { keys.all { it[16] in "89ab" } }
      case("distinct") { keys.toSet().size }
    }
  }
}
