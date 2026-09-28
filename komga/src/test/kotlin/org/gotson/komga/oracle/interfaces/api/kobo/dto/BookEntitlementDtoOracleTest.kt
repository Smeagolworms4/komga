package org.gotson.komga.oracle.interfaces.api.kobo.dto

import org.gotson.komga.domain.model.SyncPoint
import org.gotson.komga.interfaces.api.kobo.dto.toBookEntitlementDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookEntitlementDtoOracleTest : OracleTest() {
  private val date = ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)
  private val book = SyncPoint.Book("SP1", "B1", date, date.plusDays(1), date.plusDays(2), 1234, "hash", date.plusDays(3), "T1", false)

  override fun cases() {
    func("toBookEntitlementDto") {
      case("not removed") { WebOracle.stableText(WebOracle.mapper.writeValueAsString(book.toBookEntitlementDto(false))) }
      case("removed") { WebOracle.stableText(WebOracle.mapper.writeValueAsString(book.copy(thumbnailId = null, synced = true).toBookEntitlementDto(true))) }
    }
  }
}
