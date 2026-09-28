package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.SeriesMetadataUpdateDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read

class SeriesMetadataUpdateDtoOracleTest : OracleTest() {
  private val props =
    listOf("readingDirection", "ageRating", "genres", "tags", "totalBookCount", "sharingLabels", "links", "alternateTitles", "summary", "title")

  private fun state(json: String) =
    read<SeriesMetadataUpdateDto>(json).let { d ->
      props.map { d.isSet(it) } +
        listOf(d.status, d.title, d.summary, d.readingDirection, d.ageRating, d.genres, d.tags, d.totalBookCount, d.sharingLabels, d.language)
    }

  override fun cases() {
    func("isSet") {
      case("empty body") { state("{}") }
      case("nulls") {
        state("""{"readingDirection":null,"ageRating":null,"genres":null,"tags":null,"totalBookCount":null,"sharingLabels":null,"links":null,"alternateTitles":null,"summary":null}""")
      }
      case("values") {
        state("""{"status":"ENDED","title":"t","summary":"s","readingDirection":"WEBTOON","ageRating":12,"genres":["b","a"],"tags":[],"totalBookCount":5,"sharingLabels":["x"],"language":"fr"}""")
      }
    }
  }
}
