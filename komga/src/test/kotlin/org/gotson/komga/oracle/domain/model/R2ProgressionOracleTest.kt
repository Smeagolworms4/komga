package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.R2Locator
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.toR2Progression
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class R2ProgressionOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun progress(
    readDate: LocalDateTime,
    deviceId: String = "",
    deviceName: String = "",
    locator: R2Locator? = null,
  ) = ReadProgress("B1", "U1", 5, false, readDate, deviceId, deviceName, locator, date)

  override fun cases() {
    func("toR2Progression") {
      case("defaults") { progress(LocalDateTime.of(2021, 1, 15, 10, 20, 30)).toR2Progression() }
      case("summer time") { progress(LocalDateTime.of(2021, 7, 15, 10, 20, 30, 123456789), "dev", "Kobo").toR2Progression() }
      case("dst gap") { progress(LocalDateTime.of(2021, 3, 28, 2, 30)).toR2Progression().modified }
      case("dst overlap") { progress(LocalDateTime.of(2021, 10, 31, 2, 30)).toR2Progression().modified }
      case("epoch") { progress(LocalDateTime.of(1970, 1, 1, 0, 0)).toR2Progression().modified }
      case("far") { progress(LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999999)).toR2Progression().modified }
      case("with locator") {
        progress(
          date,
          "d",
          "n",
          R2Locator("ch1.xhtml", "application/xhtml+xml", "Chapter", R2Locator.Location(listOf("f"), 0.1f, 3, 0.25f), R2Locator.Text("a", "b", "h"), "span"),
        ).toR2Progression()
      }
      case("unicode device") { progress(date, "é", "漫画 reader").toR2Progression().device }
    }
  }
}
