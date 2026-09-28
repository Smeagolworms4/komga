package org.gotson.komga.oracle.interfaces.api.kobo.dto

import org.gotson.komga.domain.model.SyncPoint
import org.gotson.komga.interfaces.api.kobo.dto.TagItemDto
import org.gotson.komga.interfaces.api.kobo.dto.toWrappedTagDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TagDtoOracleTest : OracleTest() {
  private val readList =
    SyncPoint.ReadList("SP1", "R1", "My \"list\" é", ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC), ZonedDateTime.of(2021, 1, 2, 3, 4, 5, 999000000, ZoneOffset.UTC), false)

  override fun cases() {
    func("toWrappedTagDto") {
      case("no items") { WebOracle.mapper.writeValueAsString(readList.toWrappedTagDto()) }
      case("empty items") { WebOracle.mapper.writeValueAsString(readList.toWrappedTagDto(emptyList())) }
      case("items") { WebOracle.mapper.writeValueAsString(readList.toWrappedTagDto(listOf(TagItemDto("B1"), TagItemDto("B2")))) }
      case("on deck") { WebOracle.mapper.writeValueAsString(readList.copy(readListId = SyncPoint.ReadList.ON_DECK_ID, readListName = "On Deck").toWrappedTagDto(listOf(TagItemDto("B3")))) }
    }
  }
}
