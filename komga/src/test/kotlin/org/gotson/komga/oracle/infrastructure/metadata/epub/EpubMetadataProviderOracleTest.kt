package org.gotson.komga.oracle.infrastructure.metadata.epub

import org.apache.commons.validator.routines.ISBNValidator
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MetadataPatchTarget
import org.gotson.komga.infrastructure.metadata.epub.EpubMetadataProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples
import java.nio.file.Path
import kotlin.io.path.createDirectories

class EpubMetadataProviderOracleTest : OracleTest() {
  private val provider = EpubMetadataProvider(ISBNValidator(true))

  private val container =
    "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>"

  private fun epub(
    name: String,
    opf: String,
  ): BookWithMedia {
    val p: Path = tempDir.resolve("epubs").createDirectories().resolve("$name.epub")
    OracleZip.write(p, listOf(t("mimetype", "application/epub+zip"), t("META-INF/container.xml", container), t("OEBPS/content.opf", opf)))
    return BookWithMedia(MetadataSamples.book(p.toUri().toURL()), Media(mediaType = "application/epub+zip"))
  }

  private fun dated(date: String) =
    "<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:date>$date</dc:date></metadata></package>"

  override fun cases() {
    val cases = MetadataSamples.epubCases
    func("getBookMetadataFromBook") {
      cases.forEachIndexed { i, opf -> case("#$i") { provider.getBookMetadataFromBook(epub("book-$i", opf)) } }
      case("not an epub media type") { provider.getBookMetadataFromBook(epub("cbz", cases[0]).let { it.copy(media = Media(mediaType = "application/zip")) }) }
      case("missing file") { exceptionType { provider.getBookMetadataFromBook(epub("x", cases[0]).copy(book = MetadataSamples.book(tempDir.resolve("missing.epub").toUri().toURL()))) } }
    }
    func("getSeriesMetadataFromBook") {
      cases.forEachIndexed { i, opf ->
        for (append in listOf(true, false)) case("#$i (append volume $append)") { provider.getSeriesMetadataFromBook(epub("series-$i", opf), append) }
      }
      case("not an epub media type") { provider.getSeriesMetadataFromBook(epub("cbz2", cases[0]).copy(media = Media(mediaType = "application/pdf")), true) }
    }
    func("shouldLibraryHandlePatch") {
      for ((name, library) in MetadataSamples.libraries) {
        for (target in MetadataPatchTarget.entries) case("$name, $target") { provider.shouldLibraryHandlePatch(library, target) }
      }
    }
    // private: through getBookMetadataFromBook
    func("parseDate") {
      val dates =
        listOf(
          "2020-01-02", "2020-1-2", "2020-01-02Z", "2020-01-02+01:00", "2020-01-02T10:15:30", "2020-01-02T10:15:30Z", "2020-01-02T10:15:30+05:00",
          "2020-01-02T10:15:30.123456789-03:30", "2020-01-02T10:15:30[Europe/Paris]", "2020-01-02T10:15:30+01:00[Europe/Paris]", "2020", "2020-01",
          "", " 2020-01-02 ", "2020-02-30", "2019-02-29", "2020-13-01", "+12020-01-02", "-0001-01-01", "0000-01-01", "02/01/2020", "2020-01-02T25:00",
          "2020-01-02T10:15", "2020-01-02t10:15:30z", "2020-01-02 10:15:30", "2020-W01-1", "2020-001",
        )
      dates.forEachIndexed { i, d -> case("'$d'") { provider.getBookMetadataFromBook(epub("date-$i", dated(d)))?.releaseDate } }
    }
  }
}
