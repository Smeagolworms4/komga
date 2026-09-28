package org.gotson.komga.oracle.infrastructure.metadata.comicrack.dto

import org.gotson.komga.infrastructure.metadata.comicrack.dto.Book
import org.gotson.komga.infrastructure.metadata.comicrack.dto.ReadingList
import org.gotson.komga.oracle.OracleTest

class ReadingListOracleTest : OracleTest() {
  override fun cases() {
    func("toString") {
      case("empty") { ReadingList().toString() }
      case("name only") { ReadingList().also { it.name = "My list" }.toString() }
      case("blank name") { ReadingList().also { it.name = "" }.toString() }
      case("books") {
        ReadingList()
          .also {
            it.name = "L"
            it.books =
              listOf(
                Book().also { b -> b.series = "A" },
                Book().also { b ->
                  b.series = "B"
                  b.number = "2"
                  b.volume = 3
                  b.year = 2000
                  b.fileName = "f"
                },
              )
          }.toString()
      }
      case("one empty book") { ReadingList().also { it.books = listOf(Book()) }.toString() }
    }
  }
}
