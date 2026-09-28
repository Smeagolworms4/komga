package org.gotson.komga.oracle.infrastructure.validation

import org.gotson.komga.infrastructure.validation.BlankValidator
import org.gotson.komga.oracle.OracleTest

class BlankOracleTest : OracleTest() {
  override fun cases() {
    val v = BlankValidator()
    func("isValid") {
      case("null") { v.isValid(null, null) }
      case("empty") { v.isValid("", null) }
      case("[ ]") { v.isValid(" ", null) }
      case("[tn]") { v.isValid("\t\n", null) }
      case("[u00a0]") { v.isValid("\u00a0", null) }
      case("[u2003]") { v.isValid("\u2003", null) }
      case("[u3000]") { v.isValid("\u3000", null) }
      case("[u200b]") { v.isValid("\u200b", null) }
      case("[ufeff]") { v.isValid("\ufeff", null) }
      case("[u001c]") { v.isValid("\u001c", null) }
      case("[a]") { v.isValid("a", null) }
      case("[ a ]") { v.isValid(" a ", null) }
      case("[_]") { v.isValid("_", null) }
    }
  }
}
