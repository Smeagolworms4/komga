package org.gotson.komga.oracle.infrastructure.validation

import org.gotson.komga.infrastructure.validation.BCP47Validator
import org.gotson.komga.oracle.OracleTest

class BCP47OracleTest : OracleTest() {
  override fun cases() {
    val v = BCP47Validator()
    func("isValid") {
      case("null") { v.isValid(null, null) }
      case("empty") { v.isValid("", null) }
      case("[ ]") { v.isValid(" ", null) }
      case("[en]") { v.isValid("en", null) }
      case("[EN]") { v.isValid("EN", null) }
      case("[en-US]") { v.isValid("en-US", null) }
      case("[en_US]") { v.isValid("en_US", null) }
      case("[fr-FR]") { v.isValid("fr-FR", null) }
      case("[zh-Hant-TW]") { v.isValid("zh-Hant-TW", null) }
      case("[und]") { v.isValid("und", null) }
      case("[i-klingon]") { v.isValid("i-klingon", null) }
      case("[x-private]") { v.isValid("x-private", null) }
      case("[en-x-foo]") { v.isValid("en-x-foo", null) }
      case("[sr-Latn-RS]") { v.isValid("sr-Latn-RS", null) }
      case("[de-CH-1996]") { v.isValid("de-CH-1996", null) }
      case("[abcdefghi]") { v.isValid("abcdefghi", null) }
      case("[e]") { v.isValid("e", null) }
      case("[en-]") { v.isValid("en-", null) }
      case("[-en]") { v.isValid("-en", null) }
      case("[ en]") { v.isValid(" en", null) }
      case("[fra]") { v.isValid("fra", null) }
      case("[ja-JP-u-ca-japanese]") { v.isValid("ja-JP-u-ca-japanese", null) }
      case("[123]") { v.isValid("123", null) }
      case("[qaa]") { v.isValid("qaa", null) }
      case("[zz]") { v.isValid("zz", null) }
      case("[éé]") { v.isValid("éé", null) }
    }
  }
}
