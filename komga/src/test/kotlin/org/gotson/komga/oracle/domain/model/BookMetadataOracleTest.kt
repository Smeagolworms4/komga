package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.oracle.OracleTest
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

class BookMetadataOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val minimal = BookMetadata(title = " T ", number = " 1 ", numberSort = 1f, bookId = "B1", createdDate = date)
  private val full =
    BookMetadata(
      " Été ",
      " summary\n",
      " 1.5 ",
      1.5f,
      LocalDate.of(2021, 2, 28),
      listOf(Author(" John ", "WRITER"), Author("Jane", "penciller")),
      setOf("TAG", "tag ", " ", "Other"),
      "9781234567897",
      listOf(WebLink("Site", URI("https://example.org/a?b=c"))),
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      "B2",
      LocalDateTime.of(2021, 1, 2, 3, 4, 5, 6),
      LocalDateTime.of(2022, 1, 2, 3, 4),
    )

  override fun cases() {
    func("<init>") {
      case("minimal") { minimal }
      case("full") { full }
      case("float number sort") { listOf(0.1f, -0f, 1e10f, Float.NaN, Float.POSITIVE_INFINITY, 3.3333333f).map { BookMetadata(title = "", number = "", numberSort = it, createdDate = date).numberSort } }
    }
    func("copy") {
      case("no change") { full.copy() }
      case("renormalizes values") { full.copy(title = "  New ", summary = " s ", number = " 2 ", tags = setOf("Y", "y", "")) }
      case("nullables") { full.copy(releaseDate = null) }
      case("authors copied") { full.copy().authors.let { listOf(it == full.authors, it === full.authors) } }
      case("lists and locks") { minimal.copy(authors = emptyList(), links = listOf(WebLink("a", URI("file:/x"))), isbn = "", titleLock = true, linksLock = true, bookId = "") }
      case("number sort") { minimal.copy(numberSort = 0.3f).numberSort }
      case("dates") { minimal.copy(createdDate = LocalDateTime.of(2019, 1, 1, 0, 0), lastModifiedDate = LocalDateTime.of(2018, 1, 1, 0, 0)) }
    }
    func("toString") {
      case("minimal") { minimal.toString() }
      case("full") { full.toString() }
      case("number sorts") { listOf(0f, 0.1f, 1e10f, 1.0E-5f, 123456.79f, -2.5f, Float.NaN).map { minimal.copy(numberSort = it).toString().substringBefore(",") } }
      case("quotes") { BookMetadata(title = "it's", number = "'", numberSort = 0f, isbn = "'", createdDate = date).toString() }
    }
  }
}
