package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaExtensionEpub
import org.gotson.komga.domain.model.TypedBytes
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import java.net.URL
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class BookAnalyzerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val analyzer = graph.bookAnalyzer

  private val dir by lazy { tempDir.resolve("books").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }
  private val jpg by lazy { resource("barcode/page_384.jpg") }

  private fun bk(
    id: String,
    url: URL,
  ) = book(id, "S1", "L1", url = url)

  private val files by lazy {
    listOf(
      "cbz" to zipFile(dir, "b1.cbz", listOf("p1.png" to png, "p2.jpg" to jpg, t("info.txt", "hello"), "dir/" to null, "zz.png" to png)),
      "cbz without image" to zipFile(dir, "noimage.cbz", listOf(t("a.txt", "a"))),
      "cbz with unknown entry" to zipFile(dir, "unknown.cbz", listOf("p1.png" to png, "blob" to oracleBytes(0))),
      "empty zip" to zipFile(dir, "empty.cbz", emptyList()),
      "garbage cbz" to URL("file:" + dir.resolve("garbage.cbz").also { it.writeBytes(oracleBytes(100)) }),
      "text as epub" to URL("file:" + dir.resolve("text.epub").also { it.writeBytes("hello".toByteArray()) }),
      "zip as epub" to URL("file:" + Samples.komgaRes("archives/zip-as-epub.epub").toAbsolutePath()),
      "missing" to URL("file:$dir/missing.cbz"),
      "zip.zip" to URL("file:" + Samples.komgaRes("archives/zip.zip").toAbsolutePath()),
      "rar4" to URL("file:" + Samples.komgaRes("archives/rar4.rar").toAbsolutePath()),
      "rar5" to URL("file:" + Samples.komgaRes("archives/rar5.rar").toAbsolutePath()),
      "rar4 encrypted" to URL("file:" + Samples.komgaRes("archives/rar4-encrypted.rar").toAbsolutePath()),
      "7zip" to URL("file:" + Samples.komgaRes("archives/7zip.7z").toAbsolutePath()),
      "epub3" to URL("file:" + Samples.komgaRes("archives/epub3.epub").toAbsolutePath()),
      "divina epub" to URL("file:" + Samples.fixture("epub/divina.epub").toAbsolutePath()),
      "reflow epub" to URL("file:" + Samples.fixture("epub/reflow.epub").toAbsolutePath()),
      "kepub" to URL("file:" + Samples.fixture("epub/kepub.epub").toAbsolutePath()),
      "missing opf epub" to URL("file:" + Samples.fixture("epub/missing-opf.epub").toAbsolutePath()),
      "pdf" to URL("file:" + Samples.fixture("pdf/komga.pdf").toAbsolutePath()),
      "encrypted pdf" to URL("file:" + Samples.fixture("pdf/enc-user.pdf").toAbsolutePath()),
      "not a pdf" to URL("file:" + Samples.fixture("pdf/not-a-pdf.pdf").toAbsolutePath()),
      "rar with dirs" to URL("file:" + Samples.fixture("rar/r4-dirs.rar").toAbsolutePath()),
    )
  }

  private val analyzed = linkedMapOf<String, BookWithMedia>()

  private fun med(m: Media) =
    listOf(
      m.status,
      m.mediaType,
      m.pageCount,
      m.pages,
      m.files,
      m.comment,
      m.bookId,
      m.epubDivinaCompatible,
      m.epubIsKepub,
      (m.extension as? MediaExtensionEpub)?.let { listOf(it.isFixedLayout, it.positions.size, it.toc.size, it.landmarks.size, it.pageList.size) },
    )

  private fun typed(t: TypedBytes?) = t?.let { listOf(Samples.digest(it.bytes), it.mediaType) }

  /** Machine-dependent paths: temporary directory, media container fixtures, Komga test resources */
  private val replacements by lazy {
    listOf(
      dir.toString() to "<tmp>",
      Samples.fixture("").toAbsolutePath().toString() to "<fixtures>",
      Samples.komgaRes("").toAbsolutePath().toString() to "<resources>",
    )
  }

  private fun run(block: () -> Any?) = attempt(replacements, block)

  private fun ready(label: String) = analyzed.getValue(label)

  override fun cases() {
    func("analyze") {
      for ((label, url) in files) {
        for (dims in listOf(true, false)) {
          case("$label, dimensions $dims") {
            run {
              val m = analyzer.analyze(bk("B-$label", url), dims)
              if (dims) analyzed[label] = BookWithMedia(bk("B-$label", url), m)
              med(m)
            }
          }
        }
      }
    }
    func("analyzeDivina") {
      case("pages and files") { med(ready("cbz").media) }
      case("unknown entries") { med(ready("cbz with unknown entry").media) }
    }
    func("analyzeEpub") {
      case("reflow") { med(ready("reflow epub").media) }
      case("divina") { med(ready("divina epub").media) }
    }
    func("analyzePdf") {
      case("komga.pdf") { med(ready("pdf").media) }
    }
    func("generateThumbnail") {
      for (label in listOf("cbz", "zip.zip", "rar4", "divina epub", "reflow epub", "epub3", "pdf", "missing", "empty zip")) {
        case(label) {
          run {
            analyzer.generateThumbnail(ready(label)).let { listOf(it.type, it.mediaType, it.dimension, it.bookId, it.selected, graph.describeImage(it.thumbnail)) }
          }
        }
      }
      case("no cover") {
        run {
          analyzer.generateThumbnail(ready("reflow epub").let { it.copy(media = it.media.copy(epubDivinaCompatible = false)) }).let {
            listOf(it.type, it.mediaType, it.dimension, it.bookId, it.selected, graph.describeImage(it.thumbnail))
          }
        }
      }
    }
    func("getPoster@267") {
      for (label in listOf("cbz", "zip.zip", "rar4", "rar5", "divina epub", "reflow epub", "epub3", "kepub", "7zip", "missing")) {
        case(label) { run { typed(analyzer.getPoster(ready(label))) } }
      }
      case("pdf") { run { analyzer.getPoster(ready("pdf"))?.let { listOf(graph.describeImage(it.bytes), it.mediaType) } } }
      case("media without profile") { analyzer.getPoster(BookWithMedia(bk("X", URL("file:/x")), Media(status = Media.Status.READY))) }
    }
    func("getPoster@275") {
      case("first page of zip") { run { typed(analyzer.getPoster(ready("cbz"))) } }
      case("divina without pages") { run { analyzer.getPoster(ready("cbz").let { it.copy(media = it.media.copy(pages = emptyList())) }) } }
      case("first page missing in archive") {
        run { analyzer.getPoster(ready("cbz").let { it.copy(media = it.media.copy(pages = listOf(BookPage("nope.png", "image/png")))) }) }
      }
    }
    func("getPageContent") {
      case("zip page 1") { run { Samples.digest(analyzer.getPageContent(ready("cbz"), 1)) } }
      case("zip page 3") { run { Samples.digest(analyzer.getPageContent(ready("cbz"), 3)) } }
      case("zip page 0") { run { analyzer.getPageContent(ready("cbz"), 0) } }
      case("zip page 4") { run { analyzer.getPageContent(ready("cbz"), 4) } }
      case("rar page") { run { Samples.digest(analyzer.getPageContent(ready("rar5"), 1)) } }
      case("epub divina page") { run { Samples.digest(analyzer.getPageContent(ready("divina epub"), 3)) } }
      case("epub reflow") { run { analyzer.getPageContent(ready("reflow epub"), 1) } }
      case("pdf page") { run { graph.describeImage(analyzer.getPageContent(ready("pdf"), 1)) } }
      case("not ready") { run { analyzer.getPageContent(ready("missing"), 1) } }
      case("no profile") { run { analyzer.getPageContent(BookWithMedia(bk("X", URL("file:/x")), Media(status = Media.Status.READY, pageCount = 1)), 1) } }
      case("entry missing") { run { exceptionType { analyzer.getPageContent(ready("cbz").let { it.copy(media = it.media.copy(pages = listOf(BookPage("nope.png", "image/png")))) }, 1) } } }
    }
    func("getPageContentRaw") {
      case("pdf page 1") {
        run { analyzer.getPageContentRaw(ready("pdf"), 1).let { listOf(it.mediaType, String(it.bytes.copyOfRange(0, 5))) } }
      }
      case("pdf page 0") { run { analyzer.getPageContentRaw(ready("pdf"), 0) } }
      case("pdf page after last") { run { analyzer.getPageContentRaw(ready("pdf"), 99) } }
      case("zip") { run { analyzer.getPageContentRaw(ready("cbz"), 1) } }
      case("pdf not ready") { run { analyzer.getPageContentRaw(ready("pdf").let { it.copy(media = it.media.copy(status = Media.Status.OUTDATED)) }, 1) } }
    }
    func("getFileContent") {
      case("zip file") { run { String(analyzer.getFileContent(ready("cbz"), "info.txt")) } }
      case("zip page file") { run { Samples.digest(analyzer.getFileContent(ready("cbz"), "p1.png")) } }
      case("zip missing entry") { run { exceptionType { analyzer.getFileContent(ready("cbz"), "nope") } } }
      case("epub file") { run { Samples.digest(analyzer.getFileContent(ready("reflow epub"), "OPS/c1.xhtml")) } }
      case("epub missing entry") { run { exceptionType { analyzer.getFileContent(ready("reflow epub"), "nope") } } }
      case("pdf") { run { analyzer.getFileContent(ready("pdf"), "x") } }
      case("not ready") { run { analyzer.getFileContent(ready("missing"), "x") } }
      case("no profile") { run { analyzer.getFileContent(BookWithMedia(bk("X", URL("file:/x")), Media(status = Media.Status.READY)), "x") } }
    }
    func("hashPages") {
      case("few pages") { run { analyzer.hashPages(ready("cbz")).pages } }
      case("many pages, first and last hashed") {
        run {
          val url = zipFile(dir, "many.cbz", (1..8).map { "p$it.png" to png })
          val m = analyzer.analyze(bk("BM", url), false)
          analyzer.hashPages(BookWithMedia(bk("BM", url), m)).pages.map { listOf(it.fileName, it.fileHash) }
        }
      }
      case("already hashed pages kept") {
        run { analyzer.hashPages(ready("cbz").let { it.copy(media = it.media.copy(pages = it.media.pages.map { p -> p.copy(fileHash = "KEEP") })) }).pages.map { it.fileHash } }
      }
      case("epub divina") { run { analyzer.hashPages(ready("divina epub")).pages.map { it.fileHash } } }
      case("not ready") { run { analyzer.hashPages(ready("missing")) } }
      case("no pages") { run { analyzer.hashPages(ready("reflow epub")).pages } }
    }
    func("hashPage") {
      case("png") { analyzer.hashPage(BookPage("p", "image/png"), png) }
      case("jpeg is re-encoded") { analyzer.hashPage(BookPage("p", "image/jpeg"), jpg) }
      case("jpeg bytes as png") { analyzer.hashPage(BookPage("p", "image/png"), jpg) }
      case("empty") { analyzer.hashPage(BookPage("p", "image/gif"), ByteArray(0)) }
      case("not a jpeg") { exceptionType { analyzer.hashPage(BookPage("p", "image/jpeg"), png) } }
    }
    func("getPdfPagesDynamic") {
      case("pdf") { analyzer.getPdfPagesDynamic(ready("pdf").media) }
      case("pdf without dimensions") { analyzer.getPdfPagesDynamic(ready("pdf").media.let { it.copy(pages = it.pages.map { p -> p.copy(dimension = null) }) }) }
      case("pdf synthetic dimensions") {
        analyzer.getPdfPagesDynamic(Media(status = Media.Status.READY, mediaType = "application/pdf", pages = listOf(BookPage("1", "", Dimension(595, 842)), BookPage("2", "", Dimension(0, 0)), BookPage("3", "", Dimension(10000, 5)))))
      }
      case("zip") { analyzer.getPdfPagesDynamic(ready("cbz").media) }
    }
  }
}
