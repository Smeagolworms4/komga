package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookPageNumbered
import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.domain.service.BookPageEditor
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class BookPageEditorOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val editor =
    BookPageEditor(graph.bookAnalyzer, graph.fileSystemScanner, db.bookDao, db.mediaDao, db.libraryDao, db.pageHashDao, graph.transactionTemplate, graph.publisher, db.historicalEventDao)

  private val dir by lazy { tempDir.resolve("lib").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }
  private val jpg by lazy { resource("barcode/page_384.jpg") }

  private fun addBook(
    id: String,
    path: Path,
  ): Book {
    val scanned = graph.fileSystemScanner.scanFile(path)!!.copy(id = id, seriesId = "S1", libraryId = "L1", createdDate = date, lastModifiedDate = date)
    db.bookDao.insert(scanned)
    db.bookMetadataDao.insert(metadata(scanned))
    val media = graph.bookAnalyzer.analyze(scanned, true)
    db.mediaDao.insert(graph.bookAnalyzer.hashPages(org.gotson.komga.domain.model.BookWithMedia(scanned, media)).copy(createdDate = date))
    return scanned
  }

  private fun b(id: String) = db.bookDao.findByIdOrNull(id)!!

  private fun pages(id: String) = db.mediaDao.findById(id).pages.mapIndexed { i, p -> BookPageNumbered(p.fileName, p.mediaType, p.dimension, p.fileHash, p.fileSize, i + 1) }

  private fun state(id: String) =
    attempt(dir) {
      val m = db.mediaDao.findById(id)
      listOf(
        b(id).let { listOf(it.name, it.url, it.fileLastModified) },
        listOf(m.status, m.mediaType, m.pageCount, m.pages.map { p -> listOf(p.fileName, p.mediaType, p.fileHash) }, m.files.map { f -> f.fileName }),
        Files.list(dir).use { s -> s.map { it.name }.toList().sorted() },
        db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by TYPE, BOOK_ID"),
        db.rawQuery("select HASH, ACTION, DELETE_COUNT from PAGE_HASH order by HASH"),
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  override fun cases() {
    func("removeHashedPages") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$dir")))
        db.seriesDao.insert(series("S1", "L1", URL("file:$dir")))
        addBook("Z1", Path.of(zipFile(dir, "z1.cbz", listOf("p1.png" to png, "p2.jpg" to jpg, "p3.png" to png, t("ComicInfo.xml", "<ComicInfo/>"))).toURI()))
        addBook("Z2", Path.of(zipFile(dir, "z2.cbz", listOf("a.png" to png, "b.jpg" to jpg)).toURI()))
        addBook("Z3", Path.of(zipFile(dir, "z3.cbz", listOf("a.png" to png, "b.jpg" to jpg)).toURI()))
        addBook("R4", Files.copy(Samples.komgaRes("archives/rar4.rar"), dir.resolve("rar4.cbr")))
        db.pageHashDao.insert(PageHashKnown(pages("Z1")[0].fileHash, null, PageHashKnown.Action.DELETE_AUTO, deleteCount = 2, createdDate = date), null)
        pages("Z1").map { listOf(it.fileName, it.fileHash, it.pageNumber) }
      }
      case("remove middle page") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z1"), pages("Z1").filter { it.pageNumber == 2 }) }, state("Z1"))
      }
      case("remove first page") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z1"), pages("Z1").filter { it.pageNumber == 1 }) }, state("Z1"))
      }
      case("nothing to remove") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z2"), emptyList()) }, state("Z2"))
      }
      case("page not matching") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z2"), pages("Z2").map { BookPageNumbered(it.fileName, it.mediaType, it.dimension, it.fileHash, it.fileSize, it.pageNumber + 1) }) }, state("Z2"))
      }
      case("page with other hash") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z2"), pages("Z2").take(1).map { BookPageNumbered(it.fileName, it.mediaType, it.dimension, "other", it.fileSize, it.pageNumber) }) }, state("Z2"))
      }
      case("all pages") {
        listOf(attempt(dir) { editor.removeHashedPages(b("Z3"), pages("Z3")) }, state("Z3"), attempt(dir) { editor.removeHashedPages(b("Z3"), emptyList()) })
      }
      case("not a zip") { attempt(dir) { editor.removeHashedPages(b("R4"), emptyList()) } }
      case("changed on disk") { attempt(dir) { editor.removeHashedPages(b("Z2").copy(fileLastModified = date), pages("Z2").take(1)) } }
      case("file not found") { attempt(dir) { editor.removeHashedPages(b("Z2").copy(url = URL("file:$dir/gone.cbz")), emptyList()) } }
      case("media not ready") {
        db.mediaDao.update(db.mediaDao.findById("Z2").copy(status = org.gotson.komga.domain.model.Media.Status.OUTDATED))
        attempt(dir) { editor.removeHashedPages(b("Z2"), emptyList()) }
      }
    }
  }
}
