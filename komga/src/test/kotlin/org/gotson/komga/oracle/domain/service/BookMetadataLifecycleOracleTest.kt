package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.springframework.data.domain.Pageable
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.createDirectories

class BookMetadataLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.bookMetadataLifecycle

  private val dir by lazy { tempDir.resolve("books").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun comicInfo(body: String) = t("ComicInfo.xml", "<?xml version=\"1.0\"?><ComicInfo>$body</ComicInfo>")

  private val full =
    "<Title>The title</Title><Series>Batman</Series><Number>3</Number><Summary>sum</Summary><Year>2020</Year><Month>5</Month>" +
      "<Writer>John Doe, Jane</Writer><Penciller>Pen</Penciller><Tags>b, A,</Tags><GTIN>9780306406157</GTIN>" +
      "<StoryArc>Arc A, Arc B, </StoryArc><StoryArcNumber>2, x, 4</StoryArcNumber><AlternateSeries>Alt</AlternateSeries><AlternateNumber>7</AlternateNumber>" +
      "<Web>https://example.org/a not-a-uri https://komga.org</Web>"

  private fun addBook(
    id: String,
    url: URL,
  ) {
    val bk = book(id, "S1", "L1", url = url)
    db.bookDao.insert(bk)
    db.mediaDao.insert(Media(bookId = id, createdDate = date))
    db.bookMetadataDao.insert(metadata(bk))
    graph.bookLifecycle.analyzeAndPersist(bk)
  }

  private fun b(id: String) = db.bookDao.findByIdOrNull(id)!!

  private fun state(id: String) =
    attempt(dir) {
      listOf(
        db.bookMetadataDao.findById(id).let { listOf(it.title, it.summary, it.number, it.numberSort, it.releaseDate, it.authors, it.tags, it.isbn, it.links) },
        db.readListDao.findAll(SearchContext.empty(), Pageable.unpaged()).content.map { listOf(it.name, it.bookIds) },
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  private fun lib(block: (org.gotson.komga.domain.model.Library) -> org.gotson.komga.domain.model.Library) = db.libraryDao.update(block(db.libraryDao.findById("L1")))

  override fun cases() {
    func("refreshMetadata") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$dir")))
        db.seriesDao.insert(series("S1", "L1"))
        addBook("B1", zipFile(dir, "b1.cbz", listOf("p1.png" to png, comicInfo(full))))
        addBook("B2", zipFile(dir, "b2.cbz", listOf("p1.png" to png, comicInfo("<Title>  </Title><Number>1.5</Number><StoryArc>Arc A</StoryArc>"))))
        addBook("B3", zipFile(dir, "b3.cbz", listOf("p1.png" to png)))
        addBook("B4", URL("file:" + Files.copy(Samples.fixture("epub/reflow.epub"), dir.resolve("reflow.epub"))))
        addBook("B5", zipFile(dir, "b5.cbz", listOf("p1.png" to png, t("ComicInfo.xml", "<not xml"))))
        graph.takeEvents()
        db.mediaDao.findById("B1").files
      }
      case("defaults, all capabilities") {
        lifecycle.refreshMetadata(b("B1"), BookMetadataPatchCapability.entries.toSet())
        state("B1")
      }
      case("again, read list already contains the book") {
        lifecycle.refreshMetadata(b("B1"), BookMetadataPatchCapability.entries.toSet())
        state("B1")
      }
      case("read list numbers taken") {
        lifecycle.refreshMetadata(b("B2"), BookMetadataPatchCapability.entries.toSet())
        state("B2")
      }
      case("no comic info") {
        lifecycle.refreshMetadata(b("B3"), BookMetadataPatchCapability.entries.toSet())
        state("B3")
      }
      case("epub") {
        lifecycle.refreshMetadata(b("B4"), BookMetadataPatchCapability.entries.toSet())
        state("B4")
      }
      case("invalid comic info") {
        lifecycle.refreshMetadata(b("B5"), BookMetadataPatchCapability.entries.toSet())
        state("B5")
      }
      case("unsupported capabilities only") {
        db.bookMetadataDao.update(metadata(b("B1")))
        lifecycle.refreshMetadata(b("B1"), setOf(BookMetadataPatchCapability.THUMBNAILS))
        state("B1")
      }
      case("no capability") {
        lifecycle.refreshMetadata(b("B1"), emptySet())
        state("B1")
      }
      case("title only applies the whole patch") {
        lifecycle.refreshMetadata(b("B1"), setOf(BookMetadataPatchCapability.TITLE))
        state("B1")
      }
      case("locked fields") {
        db.bookMetadataDao.update(metadata(b("B1")).copy(title = "locked", titleLock = true, tagsLock = true, authorsLock = true))
        lifecycle.refreshMetadata(b("B1"), BookMetadataPatchCapability.entries.toSet())
        state("B1")
      }
      case("book import disabled, read lists enabled") {
        db.bookMetadataDao.update(metadata(b("B1")))
        lib { it.copy(importComicInfoBook = false, importEpubBook = false, importBarcodeIsbn = false) }
        lifecycle.refreshMetadata(b("B2"), BookMetadataPatchCapability.entries.toSet())
        listOf(state("B1"), state("B2"))
      }
      case("everything disabled") {
        lib { it.copy(importComicInfoReadList = false) }
        lifecycle.refreshMetadata(b("B1"), BookMetadataPatchCapability.entries.toSet())
        state("B1")
      }
      case("isbn barcode only") {
        lib { it.copy(importBarcodeIsbn = true) }
        lifecycle.refreshMetadata(b("B1"), setOf(BookMetadataPatchCapability.ISBN))
        state("B1")
      }
      case("unknown library") { exceptionType { lifecycle.refreshMetadata(b("B1").copy(libraryId = "L9"), BookMetadataPatchCapability.entries.toSet()) } }
      case("unknown book media") { exceptionType { lifecycle.refreshMetadata(book("B9", "S1", "L1"), BookMetadataPatchCapability.entries.toSet()) } }
    }
    func("handlePatchForBookMetadata") {
      case("patch applied") {
        lib { it.copy(importComicInfoBook = true) }
        lifecycle.refreshMetadata(b("B2"), setOf(BookMetadataPatchCapability.NUMBER))
        state("B2")
      }
      case("null patch leaves metadata") {
        lifecycle.refreshMetadata(b("B3"), setOf(BookMetadataPatchCapability.NUMBER))
        state("B3")
      }
    }
  }
}
