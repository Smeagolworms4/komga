package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.AlternateTitleDto
import org.gotson.komga.interfaces.api.rest.dto.AuthorDto
import org.gotson.komga.interfaces.api.rest.dto.BookMetadataAggregationDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesMetadataDto
import org.gotson.komga.interfaces.api.rest.dto.WebLinkDto
import org.gotson.komga.interfaces.api.rest.dto.restrictUrl
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDate
import java.time.LocalDateTime

class SeriesDtoOracleTest : OracleTest() {
  private val d = LocalDateTime.of(2020, 3, 4, 5, 6, 7)
  private val s =
    SeriesDto(
      id = "S1",
      libraryId = "L1",
      name = "Series",
      url = "/data/lib/series",
      created = d,
      lastModified = d,
      fileLastModified = d,
      booksCount = 3,
      booksReadCount = 1,
      booksUnreadCount = 1,
      booksInProgressCount = 1,
      metadata =
        SeriesMetadataDto(
          "ONGOING", false, "Title", false, "title", false, "sum", false, "LEFT_TO_RIGHT", false, "pub", false,
          16, false, "en", false, setOf("g"), false, setOf("t"), false, 10, false, setOf("s"), false,
          listOf(WebLinkDto("l", "https://l")), false, listOf(AlternateTitleDto("en", "Alt")), false, d, d,
        ),
      booksMetadata = BookMetadataAggregationDto(listOf(AuthorDto("a", "writer")), setOf("t"), LocalDate.of(2020, 1, 1), "s", "1", d, d),
      deleted = false,
      oneshot = true,
    )

  override fun cases() {
    func("restrictUrl") {
      case("not restricted") { s.restrictUrl(false) }
      case("restricted") { s.restrictUrl(true).url }
      case("json restricted") { json(s.restrictUrl(true)) }
    }
  }
}
