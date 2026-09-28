package org.gotson.komga.oracle.infrastructure.metadata.comicrack.dto

import org.gotson.komga.infrastructure.metadata.comicrack.dto.Manga
import org.gotson.komga.oracle.OracleTest

class MangaOracleTest : OracleTest() {
  override fun cases() {
    func("fromValue") {
      for (v in listOf(
        "Unknown", "No", "Yes", "YesAndRightToLeft", "", "yes", "YESANDRIGHTTOLEFT", "yesandrighttoleft", "Yes And Right To Left", " Yes", "YesAndRightToLeft ",
        "YES_AND_RIGHT_TO_LEFT", "UNKNOWN", "NO",
      )) {
        case("'$v'") { Manga.fromValue(v) }
      }
    }
  }
}
