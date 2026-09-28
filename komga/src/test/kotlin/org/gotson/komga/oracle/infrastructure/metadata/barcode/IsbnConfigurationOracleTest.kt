package org.gotson.komga.oracle.infrastructure.metadata.barcode

import org.gotson.komga.infrastructure.metadata.barcode.IsbnConfiguration
import org.gotson.komga.oracle.OracleTest

class IsbnConfigurationOracleTest : OracleTest() {
  override fun cases() {
    // the ISBNValidator itself is not comparable: its behaviour (ISBN-10 conversion enabled) is
    func("isbnValidator") {
      val validator = IsbnConfiguration().isbnValidator()
      for (v in listOf(
        "9782811632397", "978-2-8116-3239-7", "978 2 8116 3239 7", "2811632395", "2-8116-3239-5", "281163239X", "0306406152", "0-306-40615-2", "9780306406157",
        "9790000000001", "9782811632398", "", "abc", "ISBN 9782811632397", "97828116323970", "123456789X", "080442957X", "080442957x", " 9782811632397",
      )) {
        case("'$v'") { listOf(validator.isValid(v), validator.validate(v), validator.isValidISBN10(v), validator.isValidISBN13(v)) }
      }
    }
  }
}
