package org.gotson.komga.oracle.interfaces

import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.infrastructure.hash.Hasher
import org.gotson.komga.infrastructure.hash.KoreaderHasher
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.image.ImageConverter
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.kobo.KepubConverter
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.mediacontainer.divina.ZipExtractor
import org.gotson.komga.infrastructure.mediacontainer.epub.EpubExtractor
import org.gotson.komga.infrastructure.mediacontainer.pdf.PdfExtractor
import org.gotson.komga.infrastructure.transaction.TransactionConfiguration
import org.gotson.komga.interfaces.api.CommonBookController
import org.gotson.komga.interfaces.api.ContentRestrictionChecker
import org.gotson.komga.interfaces.api.OpdsGenerator
import org.gotson.komga.interfaces.api.WebPubGenerator
import org.gotson.komga.oracle.OracleDb
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.support.JdbcTransactionManager

/**
 * Real Komga services of the web layer built by hand on an [OracleDb] (no Spring), mirrored by
 * test/unit/interfaces/services.ts. Events published by the services are recorded in [events].
 */
class InterfacesServices(
  val db: OracleDb,
) {
  val events = mutableListOf<Any>()
  val publisher = ApplicationEventPublisher { events.add(it) }
  val settings by lazy { KomgaSettingsProvider(db.serverSettingsDao, publisher) }
  val contentDetector by lazy { ContentDetector(TikaConfig()) }
  val imageAnalyzer by lazy { ImageAnalyzer() }
  val imageConverter by lazy { ImageConverter(imageAnalyzer, contentDetector) }
  val hasher by lazy { Hasher() }
  val koreaderHasher by lazy { KoreaderHasher() }
  val kepubConverter by lazy { KepubConverter(settings, db.bookProjectionDao, null) }
  val pdfExtractor by lazy { PdfExtractor(ImageType.JPEG, 1536F) }
  val epubExtractor by lazy { EpubExtractor(contentDetector, imageAnalyzer, kepubConverter, 15) }
  val bookAnalyzer by lazy { BookAnalyzer(contentDetector, listOf(ZipExtractor(contentDetector, imageAnalyzer)), pdfExtractor, epubExtractor, imageConverter, imageAnalyzer, hasher, 3, settings, ImageType.JPEG, ImageType.JPEG) }
  val transactionTemplate by lazy { TransactionConfiguration().transactionTemplate(JdbcTransactionManager(db.dataSource)) }
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
  val contentRestrictionChecker by lazy { ContentRestrictionChecker(db.seriesMetadataDao, db.bookDao, db.thumbnailBookDao, db.seriesDao, db.thumbnailSeriesDao) }
  val commonBookController by lazy {
    CommonBookController(db.mediaDao, db.bookDao, db.bookDtoDao, db.seriesMetadataDao, bookLifecycle, bookAnalyzer, contentRestrictionChecker, contentDetector, db.readProgressDao)
  }
  val webPubGenerator by lazy { WebPubGenerator(ImageType.JPEG, imageConverter, bookAnalyzer, db.mediaDao) }
  val opdsGenerator by lazy { OpdsGenerator(ImageType.JPEG, imageConverter, bookAnalyzer, db.mediaDao) }

  /** simple names of the recorded events, then cleared */
  fun drainEvents(): List<String> = events.map { it::class.java.simpleName }.also { events.clear() }
}
