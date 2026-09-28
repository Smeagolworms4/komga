package org.gotson.komga.oracle.infrastructure.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Library
import java.net.URL
import java.time.LocalDateTime
import java.util.Base64
import kotlin.io.path.Path

/** Shared data of the metadata oracle tests, mirrored by test/unit/infrastructure/metadata/metadataSamples.ts in KomgaJS */
object MetadataSamples {
  /** Metadata fixtures (test/infrastructure/metadata/fixtures in KomgaJS) */
  fun fixture(p: String) = Path("src/test/resources/oracle/metadata/$p")

  val date: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0)

  fun book(url: URL = URL("file:/komga/book.cbz")) = Book("book", url, date, id = "BOOK", seriesId = "SERIES", libraryId = "LIBRARY", createdDate = date)

  /** ComicInfo.xml and ComicRack reading lists: id, class, content */
  val xmlCases: List<Triple<Int, String, ByteArray>> by lazy {
    ObjectMapper().readTree(fixture("xml-cases.json").toFile()).map {
      Triple(it["id"].asInt(), it["cls"].asText(), Base64.getDecoder().decode(it["b64"].asText()))
    }
  }

  /** OPF documents */
  val epubCases: List<String> by lazy { ObjectMapper().readTree(fixture("epub-cases.json").toFile()).map { it.asText() } }

  /** series.json documents */
  val mylarCases: List<String> by lazy { ObjectMapper().readTree(fixture("mylar-cases.json").toFile()).map { it.asText() } }

  private fun lib(
    name: String,
    importComicInfoBook: Boolean = false,
    importComicInfoSeries: Boolean = false,
    importComicInfoCollection: Boolean = false,
    importComicInfoReadList: Boolean = false,
    importEpubBook: Boolean = false,
    importEpubSeries: Boolean = false,
    importMylarSeries: Boolean = false,
    importLocalArtwork: Boolean = false,
    importBarcodeIsbn: Boolean = false,
  ) = name to
    Library(
      name,
      URL("file:/komga/library"),
      importComicInfoBook = importComicInfoBook,
      importComicInfoSeries = importComicInfoSeries,
      importComicInfoCollection = importComicInfoCollection,
      importComicInfoReadList = importComicInfoReadList,
      importEpubBook = importEpubBook,
      importEpubSeries = importEpubSeries,
      importMylarSeries = importMylarSeries,
      importLocalArtwork = importLocalArtwork,
      importBarcodeIsbn = importBarcodeIsbn,
      id = "LIBRARY",
      createdDate = date,
    )

  /** Libraries with every import flag, none, or a single one */
  val libraries =
    listOf(
      "all" to Library("all", URL("file:/komga/library"), id = "LIBRARY", createdDate = date),
      lib("none"),
      lib("importComicInfoBook", importComicInfoBook = true),
      lib("importComicInfoSeries", importComicInfoSeries = true),
      lib("importComicInfoCollection", importComicInfoCollection = true),
      lib("importComicInfoReadList", importComicInfoReadList = true),
      lib("importEpubBook", importEpubBook = true),
      lib("importEpubSeries", importEpubSeries = true),
      lib("importMylarSeries", importMylarSeries = true),
      lib("importLocalArtwork", importLocalArtwork = true),
      lib("importBarcodeIsbn", importBarcodeIsbn = true),
    )
}
