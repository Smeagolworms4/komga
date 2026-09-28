package org.gotson.komga.oracle.infrastructure.mediacontainer.epub

import io.mockk.every
import io.mockk.mockk
import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.kobo.KepubConverter
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.mediacontainer.epub.EpubExtractor
import org.gotson.komga.infrastructure.mediacontainer.epub.EpubPackage
import org.gotson.komga.infrastructure.mediacontainer.epub.epub
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.digest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples.pathless
import java.net.URL
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class EpubExtractorOracleTest : OracleTest() {
  private val contentDetector = ContentDetector(TikaConfig())
  private val imageAnalyzer = ImageAnalyzer()
  private val unavailable = mockk<KepubConverter> { every { isAvailable } returns false }
  private val extractor = EpubExtractor(contentDetector, imageAnalyzer, unavailable, 15)
  private val book = Book("book", URL("file:/komga/book.epub"), LocalDateTime.of(2020, 1, 1, 0, 0), id = "BOOK", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0))

  private fun cover(tb: TypedBytes?) = tb?.let { listOf(it.mediaType, digest(it.bytes)) }

  private fun <R> withEpub(
    p: Path,
    block: (EpubPackage) -> R,
  ): Any? = pathless(p) { p.epub(block) }

  override fun cases() {
    val dir = tempDir.resolve("synthetic").createDirectories()
    val files = Samples.epubFiles(dir)
    val kepubFile = Samples.writeEpub(tempDir.resolve("kepub-source").createDirectories(), "kepub")
    var copies = 0
    val failing =
      mockk<KepubConverter> {
        every { isAvailable } returns true
        every { convertEpubToKepubWithoutChecks(any(), any()) } returns null
      }
    val converting =
      mockk<KepubConverter> {
        every { isAvailable } returns true
        every { convertEpubToKepubWithoutChecks(any(), any()) } answers { kepubFile.copyTo(tempDir.resolve("converted-${copies++}.kepub.epub")) }
      }

    func("getEntryStream") {
      for ((label, p) in files) {
        case(label) { listOf("mimetype", "META-INF/container.xml", "missing", "").map { listOf(it, pathless(p) { digest(extractor.getEntryStream(p, it)) }) } }
      }
    }
    func("isEpub") {
      for ((label, p) in files) case(label) { extractor.isEpub(p) }
      case("text file") { extractor.isEpub(tempDir.resolve("text.epub").also { it.writeText("hello") }) }
      case("missing file") { extractor.isEpub(tempDir.resolve("missing.epub")) }
    }
    func("getCover") {
      for ((label, p) in files) case(label) { pathless(p) { cover(extractor.getCover(p)) } }
    }
    func("getResources") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.getResources(it) } }
    }
    func("getDivinaPages") {
      for ((label, p) in files) {
        for (analyze in listOf(true, false)) case("$label (analyze $analyze)") { withEpub(p) { extractor.getDivinaPages(it, analyze) } }
      }
      case("letter count threshold 0") { Samples.writeEpub(dir, "divina").let { p -> withEpub(p) { EpubExtractor(contentDetector, imageAnalyzer, unavailable, 0).getDivinaPages(it, false) } } }
      case("letter count threshold 1000") { Samples.writeEpub(dir, "divina with text").let { p -> withEpub(p) { EpubExtractor(contentDetector, imageAnalyzer, unavailable, 1000).getDivinaPages(it, false) } } }
    }
    func("isKepub") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.isKepub(it, extractor.getResources(it)) } }
    }
    func("computePageCount") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.computePageCount(it) } }
    }
    func("isFixedLayout") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.isFixedLayout(it) } }
    }
    func("computePositions") {
      for ((label, p) in files) {
        case(label) {
          withEpub(p) {
            val resources = extractor.getResources(it)
            extractor.computePositions(it, book, resources, extractor.isFixedLayout(it), extractor.isKepub(it, resources))
          }
        }
        case("$label, fixed layout") { withEpub(p) { extractor.computePositions(it, book, extractor.getResources(it), true, false) } }
        case("$label, failing kepub conversion") {
          withEpub(p) { EpubExtractor(contentDetector, imageAnalyzer, failing, 15).computePositions(it, book, extractor.getResources(it), false, false) }
        }
      }
      for (name in listOf("epub3", "kepub", "fixture reflow.epub")) {
        val p = if (name.startsWith("fixture")) Samples.fixture("epub/reflow.epub") else Samples.writeEpub(dir, name)
        case("$name, kepub conversion") {
          withEpub(p) { EpubExtractor(contentDetector, imageAnalyzer, converting, 15).computePositions(it, book, extractor.getResources(it), false, false) }
        }
      }
    }
    // private: exercised through computePositions of KEPUB books
    func("computePositionsFromKoboSpan") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.computePositions(it, book, extractor.getResources(it), false, true) } }
    }
    func("getToc") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.getToc(it) } }
    }
    func("getPageList") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.getPageList(it) } }
    }
    func("getLandmarks") {
      for ((label, p) in files) case(label) { withEpub(p) { extractor.getLandmarks(it) } }
    }
  }
}
