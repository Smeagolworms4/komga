package org.gotson.komga.oracle.interfaces.api.kobo.dto

import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.interfaces.api.kobo.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import java.time.LocalDateTime

class ReadingStateDtoOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

  private fun json(p: ReadProgress) = WebOracle.mapper.writeValueAsString(p.toDto())

  override fun cases() {
    func("toDto") {
      case("in progress without locator") { json(ReadProgress("B1", "U1", 3, false, date, createdDate = date, lastModifiedDate = date.plusHours(1))) }
      case("completed") { json(ReadProgress("B1", "U1", 3, true, date, createdDate = date, lastModifiedDate = date.plusMinutes(1))) }
      case("locator with progressions") {
        json(
          ReadProgress(
            "B1",
            "U1",
            3,
            false,
            date,
            locator = R2Locator("OEBPS/ch1.xhtml", "application/xhtml+xml", koboSpan = "kobo.1.2", locations = R2Locator.Location(progression = 0.25F, totalProgression = 0.123F)),
            createdDate = date,
          ),
        )
      }
      case("locator without locations") { json(ReadProgress("B1", "U1", 1, false, date, locator = R2Locator("a.xhtml", "t"), createdDate = date)) }
      case("progression precision") {
        json(ReadProgress("B1", "U1", 1, false, date, locator = R2Locator("a", "t", locations = R2Locator.Location(progression = 0.1F, totalProgression = 1F / 3F)), createdDate = date))
      }
      case("summer time") { json(ReadProgress("B1", "U1", 1, false, date, createdDate = LocalDateTime.of(2021, 7, 1, 12, 0, 0, 500_000_000))) }
    }
  }
}
