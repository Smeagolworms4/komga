package org.gotson.komga.oracle.infrastructure.metadata.comicrack.dto

import org.gotson.komga.infrastructure.metadata.comicrack.dto.Book
import org.gotson.komga.oracle.OracleTest

class BookOracleTest : OracleTest() {
  private fun book(
    series: String?,
    number: String?,
    volume: Int?,
    year: Int?,
    fileName: String?,
  ) = Book().also {
    it.series = series
    it.number = number
    it.volume = volume
    it.year = year
    it.fileName = fileName
  }

  override fun cases() {
    func("toString") {
      case("empty") { Book().toString() }
      case("full") { book("Batman", "12", 2016, 2020, "Batman 012.cbz").toString() }
      case("blank strings") { book("", " ", 0, -1, "").toString() }
      case("special characters") { book("Été, \"quoted\" (x)", "1.5", Int.MAX_VALUE, Int.MIN_VALUE, "a\nb").toString() }
      case("null string values") { book("null", "null", null, null, "null").toString() }
    }
  }
}
