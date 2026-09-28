package org.gotson.komga.oracle.domain.service

import io.mockk.every
import io.mockk.mockk
import org.apache.commons.validator.routines.ISBNValidator
import org.apache.tika.config.TikaConfig
import org.gotson.komga.application.scheduler.LibraryScanScheduler
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.BookMetadata
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.domain.service.BookConverter
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.domain.service.BookMetadataLifecycle
import org.gotson.komga.domain.service.FileSystemScanner
import org.gotson.komga.domain.service.MetadataAggregator
import org.gotson.komga.domain.service.MetadataApplier
import org.gotson.komga.domain.service.ReadListLifecycle
import org.gotson.komga.domain.service.ReadListMatcher
import org.gotson.komga.domain.service.SeriesCollectionLifecycle
import org.gotson.komga.domain.service.SeriesLifecycle
import org.gotson.komga.domain.service.SeriesMetadataLifecycle
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.hash.Hasher
import org.gotson.komga.infrastructure.hash.KoreaderHasher
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageConverter
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.image.MosaicGenerator
import org.gotson.komga.infrastructure.kobo.KepubConverter
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.mediacontainer.divina.RarExtractor
import org.gotson.komga.infrastructure.mediacontainer.divina.ZipExtractor
import org.gotson.komga.infrastructure.mediacontainer.epub.EpubExtractor
import org.gotson.komga.infrastructure.mediacontainer.pdf.PdfExtractor
import org.gotson.komga.infrastructure.metadata.barcode.IsbnBarcodeProvider
import org.gotson.komga.infrastructure.metadata.comicrack.ComicInfoProvider
import org.gotson.komga.infrastructure.metadata.comicrack.ReadListProvider
import org.gotson.komga.infrastructure.metadata.epub.EpubMetadataProvider
import org.gotson.komga.infrastructure.metadata.localartwork.LocalArtworkProvider
import org.gotson.komga.infrastructure.metadata.mylar.MylarSeriesProvider
import org.gotson.komga.infrastructure.metadata.oneshot.OneShotSeriesProvider
import org.gotson.komga.oracle.Canon
import org.gotson.komga.oracle.Canonical
import org.gotson.komga.oracle.OracleDb
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.transaction.support.TransactionTemplate
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Komga services built by hand on an [OracleDb], for the oracle tests of `domain/service`
 * (same graph in KomgaJS test/unit/domain/service/serviceGraph.ts). Collaborators are the real ones, except:
 * the event publisher (records the events), the task scheduler (records the schedules) and the Kepub converter
 * (unavailable).
 */
class ServiceGraph(
  val db: OracleDb,
) {
  val events = mutableListOf<Any>()
  val publisher = ApplicationEventPublisher { events.add(it) }

  /** Events published since the last call */
  fun takeEvents(): List<Any> = events.toList().also { events.clear() }

  /** Media type and dimensions of an image (generated images differ in their bytes between Komga and KomgaJS) */
  fun describeImage(bytes: ByteArray?): Any? =
    bytes?.let {
      val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it))
      listOf(contentDetector.detectMediaType(java.io.ByteArrayInputStream(it)), image?.width, image?.height)
    }

  /** Tasks submitted since the last call (toString), removed from the tasks database */
  fun takeTasks(): List<String> =
    db.tasksDao
      .findAll()
      .map { t -> Regex("capabilities=\\[([^\\]]*)\\]").replace(t.toString()) { m -> "capabilities=[" + m.groupValues[1].split(", ").sorted().joinToString(", ") + "]" } }
      .map { it.replace(Regex("'[0-9A-HJKMNP-TV-Z]{13}'"), "'<tsid>'") }
      .also { db.tasksDao.deleteAll() }

  /** Records the fixed rate schedules, runs nothing (same fake in KomgaJS) */
  class FakeScheduler : TaskScheduler {
    val log = mutableListOf<String>()

    override fun scheduleAtFixedRate(
      task: Runnable,
      startTime: Instant,
      period: Duration,
    ): ScheduledFuture<*> {
      log.add("schedule $period")
      return object : ScheduledFuture<Any?> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
          log.add("cancel")
          return true
        }

        override fun isCancelled() = false

        override fun isDone() = false

        override fun get(): Any? = null

        override fun get(
          timeout: Long,
          unit: TimeUnit,
        ): Any? = null

        override fun getDelay(unit: TimeUnit): Long = 0

        override fun compareTo(other: Delayed?): Int = 0
      }
    }

    override fun schedule(
      task: Runnable,
      trigger: Trigger,
    ): ScheduledFuture<*>? = throw UnsupportedOperationException()

    override fun schedule(
      task: Runnable,
      startTime: Instant,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleAtFixedRate(
      task: Runnable,
      period: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleWithFixedDelay(
      task: Runnable,
      startTime: Instant,
      delay: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleWithFixedDelay(
      task: Runnable,
      delay: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()
  }

  val scheduler = FakeScheduler()

  val transactionTemplate by lazy { TransactionTemplate(DataSourceTransactionManager(db.dataSource)) }
  val contentDetector by lazy { ContentDetector(TikaConfig()) }
  val imageAnalyzer by lazy { ImageAnalyzer() }
  val imageConverter by lazy { ImageConverter(imageAnalyzer, contentDetector) }
  val hasher by lazy { Hasher() }
  val koreaderHasher by lazy { KoreaderHasher() }
  val settings by lazy { KomgaSettingsProvider(db.serverSettingsDao, publisher) }
  val kepubConverter by lazy { mockk<KepubConverter> { every { isAvailable } returns false } }
  val epubExtractor by lazy { EpubExtractor(contentDetector, imageAnalyzer, kepubConverter, db.properties.epubDivinaLetterCountThreshold) }
  val pdfExtractor by lazy { PdfExtractor(ImageType.JPEG, 3200F) }
  val zipExtractor by lazy { ZipExtractor(contentDetector, imageAnalyzer) }
  val rarExtractor by lazy { RarExtractor(contentDetector, imageAnalyzer) }
  val bookAnalyzer by lazy {
    BookAnalyzer(
      contentDetector,
      listOf(rarExtractor, zipExtractor),
      pdfExtractor,
      epubExtractor,
      imageConverter,
      imageAnalyzer,
      hasher,
      db.properties.pageHashing,
      settings,
      ImageType.JPEG,
      ImageType.JPEG,
    )
  }
  val mosaicGenerator by lazy { MosaicGenerator(settings, ImageType.JPEG, imageConverter) }
  val isbnValidator by lazy { ISBNValidator(true) }
  val localArtworkProvider by lazy { LocalArtworkProvider(contentDetector, imageAnalyzer) }
  val mylarSeriesProvider by lazy { MylarSeriesProvider(db.mapper) }
  val comicInfoProvider by lazy { ComicInfoProvider(bookAnalyzer = bookAnalyzer, isbnValidator = isbnValidator) }
  val epubMetadataProvider by lazy { EpubMetadataProvider(isbnValidator) }
  val isbnBarcodeProvider by lazy { IsbnBarcodeProvider(bookAnalyzer, isbnValidator) }
  val oneShotSeriesProvider by lazy { OneShotSeriesProvider(db.bookDao, db.bookMetadataDao) }
  val readListProvider by lazy { ReadListProvider() }
  val fileSystemScanner by lazy { FileSystemScanner(listOf(localArtworkProvider), listOf(localArtworkProvider, mylarSeriesProvider)) }
  val metadataApplier by lazy { MetadataApplier() }
  val metadataAggregator by lazy { MetadataAggregator() }

  val bookConverter by lazy {
    BookConverter(bookAnalyzer, fileSystemScanner, db.bookDao, db.mediaDao, db.libraryDao, transactionTemplate, publisher, db.historicalEventDao)
  }
  val taskEmitter by lazy { TaskEmitter(db.bookDao, bookConverter, db.tasksDao, publisher) }
  val libraryScanScheduler by lazy { LibraryScanScheduler(scheduler, taskEmitter) }

  val bookLifecycle by lazy {
    BookLifecycle(
      db.bookDao,
      db.mediaDao,
      db.bookMetadataDao,
      db.bookProjectionDao,
      db.readProgressDao,
      db.thumbnailBookDao,
      db.readListDao,
      db.libraryDao,
      bookAnalyzer,
      imageConverter,
      publisher,
      transactionTemplate,
      hasher,
      koreaderHasher,
      db.historicalEventDao,
      settings,
      ImageType.JPEG,
    )
  }
  val seriesLifecycle by lazy {
    SeriesLifecycle(
      db.libraryDao,
      db.bookDao,
      bookLifecycle,
      db.mediaDao,
      db.bookMetadataDao,
      db.seriesDao,
      db.thumbnailSeriesDao,
      db.seriesMetadataDao,
      db.bookMetadataAggregationDao,
      db.seriesCollectionDao,
      db.readProgressDao,
      taskEmitter,
      publisher,
      transactionTemplate,
      db.historicalEventDao,
    )
  }
  val readListMatcher by lazy { ReadListMatcher(db.readListDao, db.readListRequestDao) }
  val readListLifecycle by lazy {
    ReadListLifecycle(db.readListDao, db.thumbnailReadListDao, bookLifecycle, mosaicGenerator, readListMatcher, readListProvider, publisher, transactionTemplate)
  }
  val seriesCollectionLifecycle by lazy {
    SeriesCollectionLifecycle(db.seriesCollectionDao, db.thumbnailSeriesCollectionDao, seriesLifecycle, mosaicGenerator, publisher, transactionTemplate)
  }
  val bookMetadataLifecycle by lazy {
    BookMetadataLifecycle(
      listOf(isbnBarcodeProvider, comicInfoProvider, epubMetadataProvider),
      metadataApplier,
      db.mediaDao,
      db.bookMetadataDao,
      db.libraryDao,
      readListLifecycle,
      publisher,
    )
  }
  val seriesMetadataLifecycle by lazy {
    SeriesMetadataLifecycle(
      listOf(comicInfoProvider, epubMetadataProvider),
      listOf(mylarSeriesProvider, oneShotSeriesProvider),
      metadataApplier,
      metadataAggregator,
      db.mediaDao,
      db.bookMetadataDao,
      db.seriesMetadataDao,
      db.bookMetadataAggregationDao,
      db.libraryDao,
      db.bookDao,
      seriesCollectionLifecycle,
      publisher,
    )
  }

  companion object {
    /** Fixed date of the entities created by the cases */
    val date: LocalDateTime = LocalDateTime.of(2020, 1, 2, 3, 4, 5)

    fun library(
      id: String,
      root: URL = URL("file:/libraries/$id"),
    ) = Library(name = "lib $id", root = root, id = id, createdDate = date, lastModifiedDate = date)

    fun series(
      id: String,
      libraryId: String,
      url: URL = URL("file:/libraries/$libraryId/$id"),
    ) = Series(name = "series $id", url = url, fileLastModified = date, libraryId = libraryId, id = id, createdDate = date, lastModifiedDate = date)

    fun book(
      id: String,
      seriesId: String,
      libraryId: String,
      name: String = "book $id",
      url: URL = URL("file:/libraries/$libraryId/$seriesId/$id.cbz"),
      number: Int = 0,
    ) = Book(
      name = name,
      url = url,
      fileLastModified = date,
      number = number,
      seriesId = seriesId,
      libraryId = libraryId,
      id = id,
      createdDate = date,
      lastModifiedDate = date,
    )

    /** Canonical form of [v], stable (see OracleTest.stable), with the temporary directory [dir] replaced by `<tmp>` */
    fun scrub(
      v: Any?,
      dir: Path?,
    ): Canonical = scrub(v, listOfNotNull(dir?.let { it.toString() to "<tmp>" }))

    /** Canonical form of [v], stable, with each machine-dependent string of [replacements] replaced (in order) */
    fun scrub(
      v: Any?,
      replacements: List<Pair<String, String>>,
    ): Canonical {
      fun walk(x: Any?): Any? =
        when (x) {
          is String -> replacements.fold(x) { acc, (from, to) -> acc.replace(from, to) }
          is Map<*, *> -> x.entries.associateTo(linkedMapOf()) { it.key to walk(it.value) }
          is List<*> -> x.map { walk(it) }
          else -> x
        }
      return Canonical(walk(Canon.stable(Canon.dump(v))))
    }

    /** Komga test resource (test/resources in KomgaJS) */
    fun resource(p: String): ByteArray = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/test/resources/$p"))

    /** Writes [name] in [dir] as a ZIP of [entries] (OracleZip) and returns its URL */
    fun zipFile(
      dir: Path,
      name: String,
      entries: List<Pair<String, ByteArray?>>,
    ): URL =
      org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip
        .write(dir.resolve(name), entries)
        .toUri()
        .toURL()

    /** [scrub] of the result of [block], or of the exception it throws */
    fun attempt(
      dir: Path?,
      block: () -> Any?,
    ): Canonical = attempt(listOfNotNull(dir?.let { it.toString() to "<tmp>" }), block)

    /** [scrub] of the result of [block], or of the exception it throws */
    fun attempt(
      replacements: List<Pair<String, String>>,
      block: () -> Any?,
    ): Canonical =
      try {
        scrub(block(), replacements)
      } catch (e: Throwable) {
        scrub(Canonical(Canon.dumpThrowable(e)), replacements)
      }

    /** Media and metadata created with a book, like SeriesLifecycle.addBooks */
    fun media(bookId: String) = Media(bookId = bookId)

    fun metadata(book: Book) = BookMetadata(title = book.name, number = book.number.toString(), numberSort = book.number.toFloat(), bookId = book.id)
  }
}
