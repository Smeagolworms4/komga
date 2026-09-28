package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.BookMetadataPatch
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.SeriesMetadataPatch
import org.gotson.komga.domain.model.WebLink
import org.gotson.komga.domain.service.MetadataApplier
import org.gotson.komga.oracle.OracleTest
import java.net.URI
import java.time.LocalDate

class MetadataApplierOracleTest : OracleTest() {
  private val applier = MetadataApplier()

  private val book =
    BookMetadata(
      title = "title",
      summary = "summary",
      number = "1",
      numberSort = 1F,
      releaseDate = LocalDate.of(2000, 1, 1),
      authors = listOf(Author("a", "writer")),
      tags = setOf("t"),
      isbn = "9780000000002",
      links = listOf(WebLink("l", URI("https://example.org"))),
      bookId = "B",
      createdDate = ServiceGraph.date,
    )

  private val bookLocked =
    book.copy(
      titleLock = true,
      summaryLock = true,
      numberLock = true,
      numberSortLock = true,
      releaseDateLock = true,
      authorsLock = true,
      tagsLock = true,
      isbnLock = true,
      linksLock = true,
    )

  private val fullBookPatch =
    BookMetadataPatch(
      title = "new title",
      summary = "",
      number = "2",
      numberSort = 2.5F,
      releaseDate = LocalDate.of(2010, 2, 3),
      authors = emptyList(),
      isbn = "",
      links = listOf(WebLink("x", URI("https://x.org"))),
      tags = emptySet(),
      readLists = listOf(BookMetadataPatch.ReadListEntry("rl", 1)),
    )

  private val series =
    SeriesMetadata(
      status = SeriesMetadata.Status.ONGOING,
      title = "title",
      titleSort = "sort",
      summary = "summary",
      readingDirection = SeriesMetadata.ReadingDirection.LEFT_TO_RIGHT,
      publisher = "pub",
      ageRating = 12,
      language = "en",
      genres = setOf("g"),
      tags = setOf("t"),
      totalBookCount = 5,
      seriesId = "S",
      createdDate = ServiceGraph.date,
    )

  private val seriesLocked =
    series.copy(
      statusLock = true,
      titleLock = true,
      titleSortLock = true,
      summaryLock = true,
      readingDirectionLock = true,
      publisherLock = true,
      ageRatingLock = true,
      languageLock = true,
      genresLock = true,
      totalBookCountLock = true,
    )

  private val fullSeriesPatch =
    SeriesMetadataPatch(
      title = "new",
      titleSort = "new sort",
      status = SeriesMetadata.Status.ENDED,
      summary = "",
      readingDirection = SeriesMetadata.ReadingDirection.WEBTOON,
      publisher = "",
      ageRating = 0,
      language = "fr",
      genres = emptySet(),
      totalBookCount = 0,
      collections = setOf("c"),
    )

  private val emptySeriesPatch = SeriesMetadataPatch(null, null, null, null, null, null, null, null, null, null, emptySet())

  override fun cases() {
    func("apply@21") {
      case("empty patch") { applier.apply(BookMetadataPatch(), book) }
      case("full patch") { applier.apply(fullBookPatch, book) }
      case("full patch, all locked") { applier.apply(fullBookPatch, bookLocked) }
      case("title only") { applier.apply(BookMetadataPatch(title = "t2"), book) }
      case("title locked, summary unlocked") { applier.apply(BookMetadataPatch(title = "t2", summary = "s2"), book.copy(titleLock = true)) }
      case("tags locked") { applier.apply(BookMetadataPatch(tags = setOf("x")), book.copy(tagsLock = true)) }
      case("authors unlocked") { applier.apply(BookMetadataPatch(authors = listOf(Author("b", "penciller"))), book.copy(tagsLock = true)) }
    }
    func("apply@39") {
      case("empty patch") { applier.apply(emptySeriesPatch, series) }
      case("full patch") { applier.apply(fullSeriesPatch, series) }
      case("full patch, all locked") { applier.apply(fullSeriesPatch, seriesLocked) }
      case("status only") { applier.apply(emptySeriesPatch.copy(status = SeriesMetadata.Status.HIATUS), series) }
      case("title locked, titleSort unlocked") { applier.apply(emptySeriesPatch.copy(title = "x", titleSort = "y"), series.copy(titleLock = true)) }
      case("null fields reset nothing") { applier.apply(emptySeriesPatch, series.copy(readingDirection = null, ageRating = null, totalBookCount = null)) }
    }
    func("getIfNotLocked") {
      case("patched value, unlocked") { applier.apply(BookMetadataPatch(isbn = "123"), book).isbn }
      case("patched value, locked") { applier.apply(BookMetadataPatch(isbn = "123"), book.copy(isbnLock = true)).isbn }
      case("null patch, unlocked") { applier.apply(BookMetadataPatch(isbn = null), book).isbn }
      case("null patch, locked") { applier.apply(BookMetadataPatch(), book.copy(isbnLock = true)).isbn }
      case("nullable field patched") { applier.apply(emptySeriesPatch.copy(ageRating = 18), series.copy(ageRating = null)).ageRating }
    }
  }
}
