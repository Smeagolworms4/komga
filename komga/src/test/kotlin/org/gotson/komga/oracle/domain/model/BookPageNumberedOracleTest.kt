package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.BookPageNumbered
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.oracle.OracleTest

class BookPageNumberedOracleTest : OracleTest() {
  override fun cases() {
    func("toString") {
      case("defaults") { BookPageNumbered("001.jpg", "image/jpeg", pageNumber = 1).toString() }
      case("full") { BookPageNumbered("a b/ü.png", "image/png", Dimension(800, 1200), "hash", 123456789012L, 42).toString() }
      case("quotes and empty") { BookPageNumbered("", "'", fileHash = "'", pageNumber = -1).toString() }
      case("zero size") { BookPageNumbered("x", "y", fileSize = 0, pageNumber = Int.MAX_VALUE).toString() }
    }
    func("<init>") {
      case("canonical") { BookPageNumbered("001.jpg", "image/jpeg", Dimension(1, 2), "h", 3, 4) }
    }
  }
}
