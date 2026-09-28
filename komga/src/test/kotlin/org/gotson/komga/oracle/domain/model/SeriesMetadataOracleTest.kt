package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.AlternateTitle
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.oracle.OracleTest
import java.net.URI
import java.time.LocalDateTime

class SeriesMetadataOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val minimal = SeriesMetadata(title = "  Title  ", seriesId = "S1", createdDate = date)
  private val full =
    SeriesMetadata(
      SeriesMetadata.Status.HIATUS,
      " Été ",
      " sort ",
      " summary\n",
      SeriesMetadata.ReadingDirection.WEBTOON,
      " Pub ",
      16,
      " EN-us ",
      setOf("Action", " action", "", "Drame"),
      setOf("TAG", "tag ", " "),
      12,
      setOf("Kids", "KIDS"),
      listOf(WebLink("Site", URI("https://example.org/a?b=c"))),
      listOf(AlternateTitle("JP", "漫画")),
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      "S2",
      LocalDateTime.of(2021, 1, 2, 3, 4, 5, 6),
      LocalDateTime.of(2022, 1, 2, 3, 4),
    )

  override fun cases() {
    func("<init>") {
      case("minimal") { minimal }
      case("full") { full }
      case("invalid language") { SeriesMetadata(title = "t", language = "not a language", createdDate = date).language }
      case("blank title") { SeriesMetadata(title = " \t ", createdDate = date).let { listOf(it.title, it.titleSort) } }
    }
    func("copy") {
      case("no change") { full.copy() }
      case("renormalizes values") {
        full.copy(title = "  New ", titleSort = "  ", summary = " s ", publisher = " p ", language = "FR", genres = setOf(" X "), tags = setOf("Y", "y"), sharingLabels = setOf(" "))
      }
      case("nullables") { full.copy(readingDirection = null, ageRating = null, totalBookCount = null) }
      case("lists and locks") {
        minimal.copy(links = listOf(WebLink("a", URI("file:/x"))), alternateTitles = emptyList(), statusLock = true, alternateTitlesLock = true, seriesId = "")
      }
      case("dates") { minimal.copy(createdDate = LocalDateTime.of(2019, 1, 1, 0, 0), lastModifiedDate = LocalDateTime.of(2018, 1, 1, 0, 0)) }
      case("title sort not recomputed") { minimal.copy(title = "other").titleSort }
      case("status") { minimal.copy(status = SeriesMetadata.Status.ENDED).status }
    }
    func("toString") {
      case("minimal") { minimal.toString() }
      case("full") { full.toString() }
      case("quotes") { SeriesMetadata(title = "it's", summary = "'", createdDate = date).toString() }
    }
  }
}
