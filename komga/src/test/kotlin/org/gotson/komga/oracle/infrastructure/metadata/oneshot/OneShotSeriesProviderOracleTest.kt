package org.gotson.komga.oracle.infrastructure.metadata.oneshot

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.persistence.BookMetadataRepository
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.infrastructure.metadata.oneshot.OneShotSeriesProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples
import java.net.URL

class OneShotSeriesProviderOracleTest : OracleTest() {
  private fun series(oneshot: Boolean) = Series("series", URL("file:/komga/series"), MetadataSamples.date, id = "SERIES", oneshot = oneshot, createdDate = MetadataSamples.date)

  /** Provider whose series SERIES holds the books [ids], with the metadata [metadata] (by book id) */
  private fun provider(
    ids: List<String>,
    metadata: Map<String, BookMetadata>,
  ): OneShotSeriesProvider {
    val bookRepository = mockk<BookRepository> { every { findAllIdsBySeriesId("SERIES") } returns ids }
    val bookMetadataRepository =
      mockk<BookMetadataRepository> {
        every { findById(any()) } answers { metadata[firstArg<String>()] ?: throw NoSuchElementException("No metadata") }
      }
    return OneShotSeriesProvider(bookRepository, bookMetadataRepository)
  }

  private fun metadata(
    title: String,
    summary: String,
  ) = BookMetadata(title = title, summary = summary, number = "1", numberSort = 1F, bookId = "B1", createdDate = MetadataSamples.date)

  override fun cases() {
    func("getSeriesMetadata") {
      case("not a oneshot") { provider(listOf("B1"), mapOf("B1" to metadata("T", "S"))).getSeriesMetadata(series(false)) }
      case("oneshot") { provider(listOf("B1"), mapOf("B1" to metadata("Title", "Summary"))).getSeriesMetadata(series(true)) }
      case("oneshot with blank metadata") { provider(listOf("B1"), mapOf("B1" to metadata("", ""))).getSeriesMetadata(series(true)) }
      case("several books: first one") { provider(listOf("B2", "B1"), mapOf("B1" to metadata("One", "1"), "B2" to metadata("Two", "2"))).getSeriesMetadata(series(true)) }
      case("no book") { provider(emptyList(), emptyMap()).getSeriesMetadata(series(true)) }
      case("no metadata") { provider(listOf("B1"), emptyMap()).getSeriesMetadata(series(true)) }
    }
    func("shouldLibraryHandlePatch") {
      val provider = provider(emptyList(), emptyMap())
      for ((name, library) in MetadataSamples.libraries) {
        for (target in MetadataPatchTarget.entries) case("$name, $target") { provider.shouldLibraryHandlePatch(library, target) }
      }
    }
  }
}
