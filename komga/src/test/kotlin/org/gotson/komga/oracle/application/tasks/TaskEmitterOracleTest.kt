package org.gotson.komga.oracle.application.tasks

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.application.tasks.HIGH_PRIORITY
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.BookPageNumbered
import org.gotson.komga.domain.model.CopyMode
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.service.BookConverter
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples
import org.springframework.context.ApplicationEventPublisher
import java.net.URL

class TaskEmitterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val events = mutableListOf<String>()
  private val publisher = ApplicationEventPublisher { events += it.javaClass.simpleName }

  /** fake BookConverter (same in KomgaJS): the mismatched extension books are the books of the library whose url ends with .cbr */
  private val converter =
    mockk<BookConverter> {
      every { getMismatchedExtensionBooks(any()) } answers {
        db.bookDao.findAll().filter { it.libraryId == firstArg<Library>().id && it.url.toString().endsWith(".cbr") }
      }
    }
  private val emitter = TaskEmitter(db.bookDao, converter, db.tasksDao, publisher)

  private fun library(
    id: String,
    hashFiles: Boolean = true,
    hashKoreader: Boolean = false,
    repairExtensions: Boolean = false,
  ) = Library(name = "lib $id", root = URL("file:/${id.lowercase()}"), id = id, hashFiles = hashFiles, hashKoreader = hashKoreader, repairExtensions = repairExtensions)

  private fun book(id: String): Book = db.bookDao.findByIdOrNull(id)!!

  private val pages =
    listOf(
      BookPageNumbered("p1.jpg", "image/jpeg", fileHash = "h1", pageNumber = 1),
      BookPageNumbered("p3.jpg", "image/jpeg", fileSize = 42L, pageNumber = 3),
    )

  /** tasks saved and events published by [block] */
  private fun emitted(block: () -> Unit): List<Any?> {
    db.tasksDao.deleteAll()
    events.clear()
    block()
    return listOf(OracleDb.query(db.tasksDataSource.connection, "select ID, PRIORITY, GROUP_ID, SIMPLE_TYPE, PAYLOAD from TASK order by ID"), events.toList())
  }

  override fun cases() {
    func("scanLibrary") {
      case("sample rows") { JooqSamples.insert(db) }
      case("defaults") { emitted { emitter.scanLibrary("L1") } }
      case("deep with priority") { emitted { emitter.scanLibrary("L2", true, HIGH_PRIORITY) } }
    }
    func("emptyTrash") {
      case("defaults") { emitted { emitter.emptyTrash("L1") } }
      case("priority") { emitted { emitter.emptyTrash("L1", 1) } }
    }
    func("analyzeUnknownAndOutdatedBooks") {
      case("unknown and outdated books") { emitted { emitter.analyzeUnknownAndOutdatedBooks(library("L1")) } }
      case("none") { emitted { emitter.analyzeUnknownAndOutdatedBooks(library("L2")) } }
      case("missing library") { emitted { emitter.analyzeUnknownAndOutdatedBooks(library("NOPE")) } }
    }
    func("hashBooksWithoutHash") {
      case("hash files") { emitted { emitter.hashBooksWithoutHash(library("L1")) } }
      case("hash files disabled") { emitted { emitter.hashBooksWithoutHash(library("L1", hashFiles = false)) } }
      case("other library") { emitted { emitter.hashBooksWithoutHash(library("L2")) } }
    }
    func("hashBooksWithoutHashKoreader") {
      case("hash koreader") { emitted { emitter.hashBooksWithoutHashKoreader(library("L2", hashKoreader = true)) } }
      case("disabled") { emitted { emitter.hashBooksWithoutHashKoreader(library("L2")) } }
    }
    func("findBooksWithMissingPageHash") {
      case("defaults") { emitted { emitter.findBooksWithMissingPageHash(library("L1")) } }
      case("priority") { emitted { emitter.findBooksWithMissingPageHash(library("L3"), 0) } }
    }
    func("hashBookPages") {
      case("books") { emitted { emitter.hashBookPages(listOf("B2", "B1", "B2")) } }
      case("empty") { emitted { emitter.hashBookPages(emptySet(), 7) } }
    }
    func("findBooksToConvert") {
      case("defaults") { emitted { emitter.findBooksToConvert(library("L1")) } }
      case("priority") { emitted { emitter.findBooksToConvert(library("L1"), 3) } }
    }
    func("convertBookToCbz") {
      case("books") { emitted { emitter.convertBookToCbz(listOf(book("B5"), book("B1"))) } }
      case("empty with priority") { emitted { emitter.convertBookToCbz(emptyList(), 2) } }
    }
    func("repairExtensions") {
      case("enabled") { emitted { emitter.repairExtensions(library("L2", repairExtensions = true)) } }
      case("enabled, priority") { emitted { emitter.repairExtensions(library("L2", repairExtensions = true), 1) } }
      case("enabled, no book") { emitted { emitter.repairExtensions(library("L1", repairExtensions = true)) } }
      case("disabled") { emitted { emitter.repairExtensions(library("L2")) } }
    }
    func("findDuplicatePagesToDelete") {
      case("defaults") { emitted { emitter.findDuplicatePagesToDelete(library("L1")) } }
      case("priority") { emitted { emitter.findDuplicatePagesToDelete(library("L1"), 8) } }
    }
    func("removeDuplicatePages@128") {
      case("pages") { emitted { emitter.removeDuplicatePages("B1", pages) } }
      case("no page, priority") { emitted { emitter.removeDuplicatePages("B1", emptyList(), 5) } }
    }
    func("removeDuplicatePages@136") {
      case("map") { emitted { emitter.removeDuplicatePages(linkedMapOf("B2" to pages, "B1" to pages.take(1))) } }
      case("empty map, priority") { emitted { emitter.removeDuplicatePages(emptyMap(), 5) } }
    }
    func("analyzeBook@145") {
      case("book") { emitted { emitter.analyzeBook(book("B1")) } }
      case("priority") { emitted { emitter.analyzeBook(book("B4"), 6) } }
    }
    func("analyzeBook@152") {
      case("books") { emitted { emitter.analyzeBook(listOf(book("B1"), book("B5"))) } }
      case("empty") { emitted { emitter.analyzeBook(emptyList<Book>()) } }
    }
    func("generateBookThumbnail@161") {
      case("book id") { emitted { emitter.generateBookThumbnail("B1") } }
      case("priority") { emitted { emitter.generateBookThumbnail("B1", 2) } }
    }
    func("generateBookThumbnail@168") {
      case("book ids") { emitted { emitter.generateBookThumbnail(listOf("B3", "B1")) } }
      case("empty") { emitted { emitter.generateBookThumbnail(emptyList<String>()) } }
    }
    func("refreshBookMetadata@177") {
      case("all capabilities") { emitted { emitter.refreshBookMetadata(book("B1")) } }
      case("some capabilities") { emitted { emitter.refreshBookMetadata(book("B1"), setOf(BookMetadataPatchCapability.ISBN, BookMetadataPatchCapability.AUTHORS), 5) } }
    }
    func("refreshBookMetadata@185") {
      case("books") { emitted { emitter.refreshBookMetadata(listOf(book("B4"), book("B2"))) } }
      case("no capability") { emitted { emitter.refreshBookMetadata(listOf(book("B4")), emptySet()) } }
    }
    func("refreshSeriesMetadata") {
      case("defaults") { emitted { emitter.refreshSeriesMetadata("S1") } }
      case("priority") { emitted { emitter.refreshSeriesMetadata("S1", 1) } }
    }
    func("aggregateSeriesMetadata") {
      case("defaults") { emitted { emitter.aggregateSeriesMetadata("S2") } }
      case("priority") { emitted { emitter.aggregateSeriesMetadata("S2", 7) } }
    }
    func("refreshBookLocalArtwork@209") {
      case("book") { emitted { emitter.refreshBookLocalArtwork(book("B2")) } }
      case("priority") { emitted { emitter.refreshBookLocalArtwork(book("B2"), 3) } }
    }
    func("refreshBookLocalArtwork@216") {
      case("books") { emitted { emitter.refreshBookLocalArtwork(listOf(book("B2"), book("B7"))) } }
    }
    func("refreshSeriesLocalArtwork@225") {
      case("series id") { emitted { emitter.refreshSeriesLocalArtwork("S1") } }
    }
    func("refreshSeriesLocalArtwork@232") {
      case("series ids") { emitted { emitter.refreshSeriesLocalArtwork(listOf("S2", "S1"), 1) } }
    }
    func("importBook") {
      case("nulls") { emitted { emitter.importBook("/import/a.cbz", "S1", CopyMode.COPY, null, null) } }
      case("all fields") { emitted { emitter.importBook("/import/b.cbz", "S2", CopyMode.MOVE, "new name.cbz", "B4", 6) } }
    }
    func("rebuildIndex") {
      case("defaults") { emitted { emitter.rebuildIndex() } }
      case("entities") { emitted { emitter.rebuildIndex(2, setOf(LuceneEntity.ReadList, LuceneEntity.Collection)) } }
    }
    func("upgradeIndex") {
      case("defaults") { emitted { emitter.upgradeIndex() } }
      case("priority") { emitted { emitter.upgradeIndex(8) } }
    }
    func("deleteBook") {
      case("defaults") { emitted { emitter.deleteBook("B1") } }
    }
    func("deleteSeries") {
      case("priority") { emitted { emitter.deleteSeries("S1", 5) } }
    }
    func("findBookThumbnailsToRegenerate") {
      case("defaults") { emitted { emitter.findBookThumbnailsToRegenerate(false) } }
      case("priority") { emitted { emitter.findBookThumbnailsToRegenerate(true, 0) } }
    }
    func("submitTask") {
      case("replaces the queued task") {
        emitted {
          emitter.deleteBook("B1", 1)
          emitter.deleteBook("B1", 2)
        }
      }
    }
    func("submitTasks") {
      case("several calls") {
        emitted {
          emitter.generateBookThumbnail(listOf("B1", "B2"))
          emitter.generateBookThumbnail(listOf("B2"), 9)
        }
      }
    }
  }
}
