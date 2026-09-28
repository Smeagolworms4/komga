package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.CopyMode
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.service.BookImporter
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.zipFile
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeBytes

class BookImporterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val importer =
    BookImporter(
      graph.bookLifecycle,
      graph.fileSystemScanner,
      graph.seriesLifecycle,
      db.bookDao,
      db.mediaDao,
      db.bookMetadataDao,
      db.thumbnailBookDao,
      db.readProgressDao,
      db.readListDao,
      db.libraryDao,
      db.sidecarDao,
      graph.publisher,
      graph.taskEmitter,
      db.historicalEventDao,
      db.seriesDao,
    )

  private val lib by lazy { tempDir.resolve("lib").createDirectories() }
  private val src by lazy { tempDir.resolve("import").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun source(name: String): Path = Path.of(zipFile(src, name, listOf("p1.png" to png)).toURI())

  private fun s(id: String) = db.seriesDao.findByIdOrNull(id)!!

  private fun listing(p: Path) = Files.walk(p).use { st -> st.map { p.relativize(it).toString() }.toList().sorted() }

  private fun run(block: () -> Any?) = attempt(tempDir) { block() }

  private fun state(seriesId: String) =
    attempt(tempDir) {
      listOf(
        db.bookDao.findAllBySeriesId(seriesId).sortedBy { it.number }.map { listOf(it.name, it.url, it.number, it.oneshot, it.libraryId) },
        listing(lib),
        listing(src),
        db.sidecarDao.findAll().map { listOf(it.url, it.parentUrl, it.libraryId) }.sortedBy { it[0].toString() },
        db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by rowid"),
        graph.takeTasks(),
        graph.takeEvents(),
      )
    }

  override fun cases() {
    func("importBook") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$lib")))
        lib.resolve("s1").createDirectories()
        lib.resolve("_oneshots").createDirectories()
        graph.seriesLifecycle.createSeries(series("S1", "L1", URL("file:$lib/s1")))
        graph.seriesLifecycle.createSeries(series("S2", "L1", URL("file:$lib/s2")))
        graph.takeEvents()
        true
      }
      case("missing source") { run { importer.importBook(src.resolve("nope.cbz"), s("S1"), CopyMode.COPY) } }
      case("missing source events") { state("S1") }
      case("source inside a library") {
        val inLib = Path.of(zipFile(lib, "inlib.cbz", listOf("p1.png" to png)).toURI())
        listOf(run { importer.importBook(inLib, s("S1"), CopyMode.COPY) }, state("S1")).also { Files.delete(inLib) }
      }
      case("oneshot without upgrade id") { run { importer.importBook(source("os.cbz"), s("S1").copy(oneshot = true), CopyMode.COPY) } }
      case("copy") {
        source("book 1.cbz")
        src.resolve("book 1.jpg").writeBytes(png)
        src.resolve("book 1-2.png").writeBytes(png)
        src.resolve("other.png").writeBytes(png)
        listOf(run { importer.importBook(src.resolve("book 1.cbz"), s("S1"), CopyMode.COPY).let { listOf(it.name, it.url, it.fileSize, it.oneshot) } }, state("S1"))
      }
      case("destination exists") { listOf(run { importer.importBook(src.resolve("book 1.cbz"), s("S1"), CopyMode.COPY) }, state("S1")) }
      case("move with destination name") {
        source("orig.cbz")
        src.resolve("ORIG.jpg").writeBytes(png)
        listOf(run { importer.importBook(src.resolve("orig.cbz"), s("S1"), CopyMode.MOVE, "Renamed Book").name }, state("S1"))
      }
      case("hardlink") {
        source("linked.cbz")
        listOf(run { importer.importBook(src.resolve("linked.cbz"), s("S1"), CopyMode.HARDLINK).name }, state("S1"), Files.isSameFile(src.resolve("linked.cbz"), lib.resolve("s1/linked.cbz")))
      }
      case("series folder missing") { listOf(run { importer.importBook(source("nofolder.cbz"), s("S2"), CopyMode.COPY) }, state("S2")) }
      case("upgrade book of other series") {
        val id = db.bookDao.findAllBySeriesId("S1").first { it.name == "book 1" }.id
        exceptionType { importer.importBook(source("up.cbz"), s("S2"), CopyMode.COPY, upgradeBookId = id) }
      }
      case("upgrade unknown book") { listOf(run { importer.importBook(source("unknown.cbz"), s("S1"), CopyMode.COPY, upgradeBookId = "NOPE").name }, state("S1")) }
      case("upgrade with same file name") {
        val old = db.bookDao.findAllBySeriesId("S1").first { it.name == "book 1" }
        db.komgaUserDao.insert(org.gotson.komga.domain.model.KomgaUser("u@example.org", "p", id = "U1", createdDate = date))
        db.readProgressDao.save(ReadProgress(old.id, "U1", 1, false, date, createdDate = date))
        db.readListDao.insert(ReadList("rl", bookIds = sortedMapOf(3 to old.id, 5 to db.bookDao.findAllBySeriesId("S1").first { it.name == "linked" }.id), id = "RL", createdDate = date))
        db.thumbnailBookDao.insert(ThumbnailBook(oracleBytes(3), null, true, ThumbnailBook.Type.USER_UPLOADED, "image/jpeg", 3, Dimension(1, 1), "TU", old.id, date))
        db.bookMetadataDao.update(db.bookMetadataDao.findById(old.id).copy(title = "Kept title", titleLock = true))
        val newSrc = tempDir.resolve("import2").createDirectories()
        zipFile(newSrc, "book 1.cbz", listOf("p1.png" to png, "p2.png" to png))
        listOf(
          run {
            importer.importBook(newSrc.resolve("book 1.cbz"), s("S1"), CopyMode.MOVE, upgradeBookId = old.id).let { nb ->
              listOf(
                db.mediaDao.findById(nb.id).status,
                db.bookMetadataDao.findById(nb.id).title,
                db.readProgressDao.findAllByBookId(nb.id).map { it.userId },
                db.readListDao.findByIdOrNull("RL", SearchContext.empty())!!.bookIds.map { (k, v) -> listOf(k, v == nb.id) },
                db.thumbnailBookDao.findAllByBookId(nb.id).map { it.id },
                db.bookDao.findByIdOrNull(old.id),
              )
            }
          },
          state("S1"),
        )
      }
      case("upgrade with other file name") {
        val old = db.bookDao.findAllBySeriesId("S1").first { it.name == "linked" }
        listOf(run { importer.importBook(source("better.cbz"), s("S1"), CopyMode.COPY, upgradeBookId = old.id).name }, state("S1"))
      }
      case("oneshot upgrade") {
        val osFile = Path.of(zipFile(lib.resolve("_oneshots"), "one.cbz", listOf("p1.png" to png)).toURI())
        val os = Series(name = "one", url = osFile.toUri().toURL(), fileLastModified = date, libraryId = "L1", oneshot = true, id = "OS", createdDate = date)
        graph.seriesLifecycle.createSeries(os)
        graph.seriesLifecycle.addBooks(os, listOf(graph.fileSystemScanner.scanFile(osFile)!!.copy(libraryId = "L1", oneshot = true, id = "OSB")))
        graph.takeEvents()
        graph.takeTasks()
        listOf(
          run { importer.importBook(source("one v2.cbz"), s("OS"), CopyMode.COPY, upgradeBookId = "OSB").let { listOf(it.name, it.url, it.oneshot) } },
          state("OS"),
          attempt(tempDir) { s("OS").let { listOf(it.url, it.oneshot) } },
        )
      }
    }
  }
}
