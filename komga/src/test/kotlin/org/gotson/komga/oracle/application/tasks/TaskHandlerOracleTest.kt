package org.gotson.komga.oracle.application.tasks

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.application.tasks.Task
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.application.tasks.TaskHandler
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookAction
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.BookPageNumbered
import org.gotson.komga.domain.model.CopyMode
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.service.BookConverter
import org.gotson.komga.domain.service.BookImporter
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.domain.service.BookMetadataLifecycle
import org.gotson.komga.domain.service.BookPageEditor
import org.gotson.komga.domain.service.LibraryContentLifecycle
import org.gotson.komga.domain.service.LocalArtworkLifecycle
import org.gotson.komga.domain.service.PageHashLifecycle
import org.gotson.komga.domain.service.SeriesLifecycle
import org.gotson.komga.domain.service.SeriesMetadataLifecycle
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.SearchIndexLifecycle
import org.gotson.komga.interfaces.scheduler.METER_TASKS_EXECUTION
import org.gotson.komga.interfaces.scheduler.METER_TASKS_FAILURE
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path

/**
 * The collaborators that do the work are fakes (same in KomgaJS) recording their calls, the repositories and the
 * TaskEmitter are the real ones on the oracle database.
 */
class TaskHandlerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val log = mutableListOf<String>()

  private fun Book.s() = id

  private fun Series.s() = id

  private fun Library.s() = id

  private val pages =
    listOf(
      BookPageNumbered("p1.jpg", "image/jpeg", fileHash = "h1", pageNumber = 1),
      BookPageNumbered("p2.jpg", "image/jpeg", fileHash = "h2", pageNumber = 2),
    )

  private val converter =
    mockk<BookConverter> {
      every { getMismatchedExtensionBooks(any()) } answers { db.bookDao.findAll().filter { it.libraryId == firstArg<Library>().id && it.url.toString().endsWith(".cbr") } }
      every { getConvertibleBooks(any()) } answers {
        log += "getConvertibleBooks(${firstArg<Library>().s()})"
        db.bookDao.findAll().filter { it.libraryId == firstArg<Library>().id && it.url.toString().endsWith(".cbr") }
      }
      every { convertToCbz(any()) } answers { log += "convertToCbz(${firstArg<Book>().s()})" }
      every { repairExtension(any()) } answers {
        log += "repairExtension(${firstArg<Book>().s()})"
        if (firstArg<Book>().id == "B6") throw IllegalStateException("cannot repair")
      }
    }
  private val libraryContentLifecycle =
    mockk<LibraryContentLifecycle> {
      every { scanRootFolder(any(), any()) } answers { log += "scanRootFolder(${firstArg<Library>().s()}, ${secondArg<Boolean>()})" }
      every { emptyTrash(any()) } answers { log += "emptyTrash(${firstArg<Library>().s()})" }
    }
  private val bookLifecycle =
    mockk<BookLifecycle> {
      every { analyzeAndPersist(any()) } answers {
        val b = firstArg<Book>()
        log += "analyzeAndPersist(${b.s()})"
        when (b.id) {
          "B1" -> setOf(BookAction.GENERATE_THUMBNAIL, BookAction.REFRESH_METADATA)
          "B4" -> setOf(BookAction.REFRESH_METADATA)
          "B5" -> setOf(BookAction.GENERATE_THUMBNAIL)
          else -> emptySet()
        }
      }
      every { generateThumbnailAndPersist(any()) } answers { log += "generateThumbnailAndPersist(${firstArg<Book>().s()})" }
      every { hashAndPersist(any()) } answers { log += "hashAndPersist(${firstArg<Book>().s()})" }
      every { hashKoreaderAndPersist(any()) } answers { log += "hashKoreaderAndPersist(${firstArg<Book>().s()})" }
      every { hashPagesAndPersist(any()) } answers { log += "hashPagesAndPersist(${firstArg<Book>().s()})" }
      every { deleteBookFiles(any()) } answers { log += "deleteBookFiles(${firstArg<Book>().s()})" }
      every { findBookThumbnailsToRegenerate(any()) } answers {
        log += "findBookThumbnailsToRegenerate(${firstArg<Boolean>()})"
        if (firstArg()) listOf("B2", "B1") else emptyList()
      }
    }
  private val bookMetadataLifecycle =
    mockk<BookMetadataLifecycle> {
      every { refreshMetadata(any(), any()) } answers { log += "refreshMetadata(${firstArg<Book>().s()}, ${secondArg<Set<BookMetadataPatchCapability>>()})" }
    }
  private val seriesLifecycle =
    mockk<SeriesLifecycle> {
      every { deleteSeriesFiles(any()) } answers { log += "deleteSeriesFiles(${firstArg<Series>().s()})" }
    }
  private val seriesMetadataLifecycle =
    mockk<SeriesMetadataLifecycle> {
      every { refreshMetadata(any()) } answers { log += "refreshMetadata(${firstArg<Series>().s()})" }
      every { aggregateMetadata(any()) } answers { log += "aggregateMetadata(${firstArg<Series>().s()})" }
    }
  private val localArtworkLifecycle =
    mockk<LocalArtworkLifecycle> {
      every { refreshLocalArtwork(any<Book>()) } answers { log += "refreshLocalArtwork(book ${firstArg<Book>().s()})" }
      every { refreshLocalArtwork(any<Series>()) } answers { log += "refreshLocalArtwork(series ${firstArg<Series>().s()})" }
    }
  private val bookImporter =
    mockk<BookImporter> {
      every { importBook(any(), any(), any(), any(), any()) } answers {
        val source = firstArg<Path>().toString()
        log += "importBook($source, ${secondArg<Series>().s()}, ${thirdArg<CopyMode>()}, ${arg<String?>(3)}, ${arg<String?>(4)})"
        if (source.endsWith("fail.cbz")) throw IllegalArgumentException("import failed")
        db.bookDao.findByIdOrNull("B5")!!
      }
    }
  private val bookPageEditor =
    mockk<BookPageEditor> {
      every { removeHashedPages(any(), any()) } answers {
        log += "removeHashedPages(${firstArg<Book>().s()}, ${secondArg<Collection<BookPageNumbered>>().map { it.pageNumber }})"
        if (secondArg<Collection<BookPageNumbered>>().isEmpty()) null else BookAction.GENERATE_THUMBNAIL
      }
    }
  private val searchIndexLifecycle =
    mockk<SearchIndexLifecycle> {
      every { rebuildIndex(any()) } answers { log += "rebuildIndex(${firstArg<Set<LuceneEntity>?>()?.map { it.type }})" }
      every { upgradeIndex() } answers { log += "upgradeIndex()" }
    }
  private val pageHashLifecycle =
    mockk<PageHashLifecycle> {
      every { getBookIdsWithMissingPageHash(any()) } answers {
        log += "getBookIdsWithMissingPageHash(${firstArg<Library>().s()})"
        listOf("B1", "B4")
      }
      every { getBookPagesToDeleteAutomatically(any()) } answers {
        log += "getBookPagesToDeleteAutomatically(${firstArg<Library>().s()})"
        mapOf("B2" to pages)
      }
    }

  private val emitter = TaskEmitter(db.bookDao, converter, db.tasksDao, ApplicationEventPublisher { })

  /** calls made, tasks emitted, failure and execution counts */
  private fun handled(task: Task): List<Any?> {
    db.tasksDao.deleteAll()
    log.clear()
    val registry = SimpleMeterRegistry()
    val handler =
      TaskHandler(
        emitter,
        db.libraryDao,
        db.bookDao,
        db.seriesDao,
        libraryContentLifecycle,
        bookLifecycle,
        bookMetadataLifecycle,
        seriesLifecycle,
        seriesMetadataLifecycle,
        localArtworkLifecycle,
        bookImporter,
        converter,
        bookPageEditor,
        searchIndexLifecycle,
        pageHashLifecycle,
        registry,
      )
    handler.handleTask(task)
    val type = task.javaClass.simpleName
    return listOf(
      log.toList(),
      OracleDb.query(db.tasksDataSource.connection, "select ID, PRIORITY, GROUP_ID from TASK order by ID"),
      registry.counter(METER_TASKS_FAILURE, "type", type).count(),
      registry.timer(METER_TASKS_EXECUTION, "type", type).count(),
    )
  }

  override fun cases() {
    func("handleTask") {
      case("sample rows") { JooqSamples.insert(db) }
      case("scan library") { handled(Task.ScanLibrary("L1", true)) }
      case("scan library L2") { handled(Task.ScanLibrary("L2", false, 6)) }
      case("scan missing library") { handled(Task.ScanLibrary("NOPE", false)) }
      case("find books to convert") { handled(Task.FindBooksToConvert("L2", 2)) }
      case("find books to convert, missing library") { handled(Task.FindBooksToConvert("NOPE")) }
      case("find books with missing page hash") { handled(Task.FindBooksWithMissingPageHash("L1", 0)) }
      case("find books with missing page hash, missing library") { handled(Task.FindBooksWithMissingPageHash("NOPE")) }
      case("find duplicate pages to delete") { handled(Task.FindDuplicatePagesToDelete("L1", 1)) }
      case("find duplicate pages to delete, missing library") { handled(Task.FindDuplicatePagesToDelete("NOPE")) }
      case("empty trash") { handled(Task.EmptyTrash("L2")) }
      case("empty trash, missing library") { handled(Task.EmptyTrash("NOPE")) }
      case("analyze book, all actions") { handled(Task.AnalyzeBook("B1", 3, "S1")) }
      case("analyze book, refresh metadata") { handled(Task.AnalyzeBook("B4", groupId = "S2")) }
      case("analyze book, no action") { handled(Task.AnalyzeBook("B2", groupId = "S1")) }
      case("analyze missing book") { handled(Task.AnalyzeBook("NOPE", groupId = "S1")) }
      case("generate thumbnail") { handled(Task.GenerateBookThumbnail("B1")) }
      case("generate thumbnail, missing book") { handled(Task.GenerateBookThumbnail("NOPE")) }
      case("refresh book metadata") { handled(Task.RefreshBookMetadata("B1", setOf(BookMetadataPatchCapability.TITLE, BookMetadataPatchCapability.TAGS), 5, "S1")) }
      case("refresh book metadata, missing book") { handled(Task.RefreshBookMetadata("NOPE", emptySet(), groupId = "S1")) }
      case("refresh series metadata") { handled(Task.RefreshSeriesMetadata("S1", 2)) }
      case("refresh series metadata, missing series") { handled(Task.RefreshSeriesMetadata("NOPE")) }
      case("aggregate series metadata") { handled(Task.AggregateSeriesMetadata("S2")) }
      case("aggregate series metadata, missing series") { handled(Task.AggregateSeriesMetadata("NOPE")) }
      case("refresh book local artwork") { handled(Task.RefreshBookLocalArtwork("B3")) }
      case("refresh book local artwork, missing book") { handled(Task.RefreshBookLocalArtwork("NOPE")) }
      case("refresh series local artwork") { handled(Task.RefreshSeriesLocalArtwork("S3")) }
      case("refresh series local artwork, missing series") { handled(Task.RefreshSeriesLocalArtwork("NOPE")) }
      case("import book") { handled(Task.ImportBook("/import/new.cbz", "S1", CopyMode.HARDLINK, "dest.cbz", null, 5)) }
      case("import book failure") { handled(Task.ImportBook("/import/fail.cbz", "S1", CopyMode.MOVE, null, "B2")) }
      case("import book, missing series") { handled(Task.ImportBook("/import/new.cbz", "NOPE", CopyMode.COPY, null, null)) }
      case("convert book") { handled(Task.ConvertBook("B5", groupId = "S3")) }
      case("convert book, missing book") { handled(Task.ConvertBook("NOPE", groupId = "S3")) }
      case("repair extension") { handled(Task.RepairExtension("B5", groupId = "S3")) }
      case("repair extension failure") { handled(Task.RepairExtension("B6", groupId = "S4")) }
      case("remove hashed pages") { handled(Task.RemoveHashedPages("B1", pages, 6)) }
      case("remove hashed pages, no page") { handled(Task.RemoveHashedPages("B1", emptyList())) }
      case("remove hashed pages, missing book") { handled(Task.RemoveHashedPages("NOPE", pages)) }
      case("hash book") { handled(Task.HashBook("B1")) }
      case("hash book, missing book") { handled(Task.HashBook("NOPE")) }
      case("hash book koreader") { handled(Task.HashBookKoreader("B2")) }
      case("hash book pages") { handled(Task.HashBookPages("B4")) }
      case("hash book pages, missing book") { handled(Task.HashBookPages("NOPE")) }
      case("rebuild index") { handled(Task.RebuildIndex(null)) }
      case("rebuild index, some entities") { handled(Task.RebuildIndex(setOf(LuceneEntity.Book))) }
      case("upgrade index") { handled(Task.UpgradeIndex()) }
      case("delete book") { handled(Task.DeleteBook("B1")) }
      case("delete oneshot book") { handled(Task.DeleteBook("B6")) }
      case("delete missing book") { handled(Task.DeleteBook("NOPE")) }
      case("delete series") { handled(Task.DeleteSeries("S2")) }
      case("delete missing series") { handled(Task.DeleteSeries("NOPE")) }
      case("find thumbnails to regenerate") { handled(Task.FindBookThumbnailsToRegenerate(true, 3)) }
      case("find thumbnails to regenerate, none") { handled(Task.FindBookThumbnailsToRegenerate(false)) }
    }
  }
}
