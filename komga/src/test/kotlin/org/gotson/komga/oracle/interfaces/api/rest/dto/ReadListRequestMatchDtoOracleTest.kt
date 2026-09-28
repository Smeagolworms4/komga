package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.ReadListMatch
import org.gotson.komga.domain.model.ReadListRequestBook
import org.gotson.komga.domain.model.ReadListRequestBookMatchBook
import org.gotson.komga.domain.model.ReadListRequestBookMatchSeries
import org.gotson.komga.domain.model.ReadListRequestBookMatches
import org.gotson.komga.domain.model.ReadListRequestMatch
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDate

class ReadListRequestMatchDtoOracleTest : OracleTest() {
  private val s1 = ReadListRequestBookMatchSeries("S1", "Series 1", LocalDate.of(2020, 2, 29))
  private val s2 = ReadListRequestBookMatchSeries("S2", "Series 2", null)
  private val match =
    ReadListRequestMatch(
      ReadListMatch("List", "ERR_1"),
      listOf(
        ReadListRequestBookMatches(
          ReadListRequestBook(setOf("Series 1", "series one"), "1"),
          linkedMapOf(
            s1 to listOf(ReadListRequestBookMatchBook("B1", "1", "Book 1"), ReadListRequestBookMatchBook("B2", "1", "Book 1 bis")),
            s2 to emptyList(),
          ),
        ),
        ReadListRequestBookMatches(ReadListRequestBook(emptySet(), ""), emptyMap()),
      ),
      "ignored",
    )

  override fun cases() {
    func("toDto@15") {
      case("full, errorCode not copied") { match.toDto() }
      case("no requests") { ReadListRequestMatch(ReadListMatch("x"), emptyList()).toDto() }
      case("json") { json(match.toDto()) }
    }
    func("toDto@33") {
      case("with error") { ReadListMatch("n", "ERR_1").toDto() }
      case("default error") { ReadListMatch("").toDto() }
    }
    func("toDto@45") {
      case("series set") { ReadListRequestBook(setOf("b", "a"), "12.5").toDto() }
      case("empty") { ReadListRequestBook(emptySet(), "").toDto() }
    }
  }
}
