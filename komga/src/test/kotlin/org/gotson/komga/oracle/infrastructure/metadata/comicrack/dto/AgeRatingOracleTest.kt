package org.gotson.komga.oracle.infrastructure.metadata.comicrack.dto

import org.gotson.komga.infrastructure.metadata.comicrack.dto.AgeRating
import org.gotson.komga.oracle.OracleTest

class AgeRatingOracleTest : OracleTest() {
  private val values =
    AgeRating.entries.map { it.value } +
      listOf(
        "", " ", "unknown", "UNKNOWN", "adults only 18+", "AdultsOnly18+", "  Adults  Only 18+  ", "Adults\tOnly 18+", "Everyone 10 +", "everyone10+",
        "MA15+", "ma 15+", "Mature17+", "r 18+", "Teen ", "teen", "TEEN", "Kids to adults", "PG-13", "M ", "g", "18+", "İ",
        "ratingpending", "earlychildhood", "Everyone ", "Ｔｅｅｎ",
      )

  override fun cases() {
    func("fromValue") {
      for (v in values) case("'$v'") { AgeRating.fromValue(v)?.let { listOf(it, it.value, it.ageRating) } }
    }
    // private: through fromValue
    func("toLowerNoSpace") {
      for (v in listOf("A B C", "ÀÉÎ Õ Ü", "İstanbul", "ẞ", "  ", "Σ Σ")) case("'$v'") { AgeRating.fromValue(v) }
    }
  }
}
