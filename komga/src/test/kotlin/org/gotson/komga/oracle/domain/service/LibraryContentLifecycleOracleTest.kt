package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.domain.service.LibraryContentLifecycle
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip
import java.net.URL
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class LibraryContentLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle =
    LibraryContentLifecycle(
      graph.fileSystemScanner,
      db.seriesDao,
      db.bookDao,
      db.libraryDao,
      graph.bookLifecycle,
      db.mediaDao,
      graph.seriesLifecycle,
      graph.seriesCollectionLifecycle,
      graph.readListLifecycle,
      db.sidecarDao,
      graph.settings,
      graph.taskEmitter,
      graph.transactionTemplate,
      graph.hasher,
      db.bookMetadataDao,
      db.seriesMetadataDao,
      db.readListDao,
      db.readProgressDao,
      db.seriesCollectionDao,
      db.thumbnailBookDao,
      graph.publisher,
      db.thumbnailSeriesDao,
    )

  private val root by lazy { tempDir.resolve("lib").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private val t1 = FileTime.from(Instant.parse("2030-01-02T03:04:05Z"))
  private val t2 = FileTime.from(Instant.parse("2031-06-07T08:09:10Z"))

  /** ZIP of one page, [n] making its content (and size) unique */
  private fun cbz(
    rel: String,
    n: Int,
    time: FileTime = t1,
  ) {
    val p = root.resolve(rel)
    p.parent.createDirectories()
    OracleZip.write(p, listOf("p1.png" to png, "n.txt" to oracleBytes(n)))
    Files.setLastModifiedTime(p, time)
  }

  private fun side(
    rel: String,
    time: FileTime = t1,
  ) {
    val p = root.resolve(rel)
    p.parent.createDirectories()
    p.writeBytes(png)
    Files.setLastModifiedTime(p, time)
  }

  private fun touchDirs(time: FileTime = t1) {
    Files.walk(root).use { s -> s.filter { Files.isDirectory(it) }.toList() }.sortedDescending().forEach { Files.setLastModifiedTime(it, time) }
  }

  private fun lib() = db.libraryDao.findById("L1")

  private fun state() =
    attempt(tempDir) {
      val series = db.seriesDao.findAll().sortedBy { it.url.toString() }
      listOf(
        series.map { s ->
          listOf(
            s.name,
            s.url,
            s.fileLastModified,
            s.deletedDate != null,
            s.bookCount,
            db.seriesMetadataDao.findById(s.id).title,
            db.bookDao.findAllBySeriesId(s.id).sortedBy { it.url.toString() }.map { b ->
              listOf(b.name, b.url, b.number, b.fileSize, b.fileHash, b.deletedDate != null, db.mediaDao.findById(b.id).status, db.bookMetadataDao.findById(b.id).title)
            },
          )
        },
        db.sidecarDao.findAll().map { listOf(it.url, it.parentUrl, it.lastModifiedTime) }.sortedBy { it[0].toString() },
        graph.takeTasks().map { it.substringBefore("(") }.sorted(),
        graph.takeEvents().map { it.javaClass.simpleName },
        lib().unavailableDate != null,
      )
    }

  private fun scan(scanDeep: Boolean = false) = attempt(tempDir) { lifecycle.scanRootFolder(lib(), scanDeep) }

  override fun cases() {
    func("scanRootFolder") {
      case("setup") {
        db.libraryDao.insert(library("L1", URL("file:$root")))
        db.komgaUserDao.insert(KomgaUser("u@example.org", "p", id = "U1", createdDate = date))
        cbz("s1/a.cbz", 1)
        cbz("s1/b.cbz", 2)
        side("s1/cover.jpg")
        side("s1/a.jpg")
        cbz("s2/c.cbz", 3)
        touchDirs()
        true
      }
      case("first scan") { listOf(scan(), state()) }
      case("unchanged") { listOf(scan(), state()) }
      case("unchanged, deep") { listOf(scan(true), state()) }
      case("book modified, book added, series emptied") {
        cbz("s1/b.cbz", 20, t2)
        cbz("s1/d.cbz", 4)
        Files.delete(root.resolve("s2/c.cbz"))
        side("s1/cover.jpg", t2)
        Files.delete(root.resolve("s1/a.jpg"))
        touchDirs(t2)
        listOf(scan(), state())
      }
      case("book modified, same size and hash") {
        val b = db.bookDao.findAll().first { it.name == "b" }
        db.bookDao.update(b.copy(fileHash = graph.hasher.computeHash(b.path)))
        Files.setLastModifiedTime(root.resolve("s1/b.cbz"), t1)
        listOf(scan(true), state())
      }
      case("book modified, same size, other hash") {
        val b = db.bookDao.findAll().first { it.name == "b" }
        db.bookDao.update(b.copy(fileHash = "not the hash"))
        Files.setLastModifiedTime(root.resolve("s1/b.cbz"), t2)
        listOf(scan(true), state())
      }
      case("series folder renamed, restored") {
        db.bookDao.findAll().filter { it.deletedDate == null }.forEach { db.bookDao.update(it.copy(fileHash = graph.hasher.computeHash(it.path))) }
        val s1 = db.seriesDao.findAll().first { it.name == "s1" }
        db.seriesMetadataDao.update(db.seriesMetadataDao.findById(s1.id).copy(title = "Locked title", titleLock = true, summary = "kept"))
        db.seriesCollectionDao.insert(SeriesCollection("col", seriesIds = listOf(s1.id), id = "C1", createdDate = date))
        db.thumbnailSeriesDao.insert(ThumbnailSeries(oracleBytes(3), null, true, ThumbnailSeries.Type.USER_UPLOADED, "image/jpeg", 3, Dimension(1, 1), "TS", s1.id, date))
        val a = db.bookDao.findAll().first { it.name == "a" }
        db.readProgressDao.save(ReadProgress(a.id, "U1", 1, false, date, createdDate = date))
        db.readListDao.insert(ReadList("rl", bookIds = sortedMapOf(1 to a.id), id = "RL", createdDate = date))
        db.bookMetadataDao.update(db.bookMetadataDao.findById(a.id).copy(title = "Locked book", titleLock = true))
        Files.move(root.resolve("s1"), root.resolve("s1 moved"))
        touchDirs(t2)
        listOf(
          scan(),
          state(),
          attempt(tempDir) {
            val moved = db.seriesDao.findAll().first { it.name == "s1 moved" }
            listOf(
              db.seriesMetadataDao.findById(moved.id).let { listOf(it.title, it.summary) },
              db.seriesCollectionDao.findByIdOrNull("C1", org.gotson.komga.domain.model.SearchContext.empty())!!.seriesIds.map { it == moved.id },
              db.thumbnailSeriesDao.findAllBySeriesId(moved.id).map { it.id },
              db.readProgressDao.findAll().map { db.bookDao.findByIdOrNull(it.bookId)?.name },
              db.readListDao.findByIdOrNull("RL", org.gotson.komga.domain.model.SearchContext.empty())!!.bookIds.values.map { db.bookDao.findByIdOrNull(it)?.name },
            )
          },
        )
      }
      case("book renamed, restored") {
        Files.move(root.resolve("s1 moved/d.cbz"), root.resolve("s1 moved/d2.cbz"))
        touchDirs(t1)
        listOf(scan(), state())
      }
      case("root missing") {
        Files.move(root, tempDir.resolve("lib-away"))
        listOf(scan(), state())
      }
      case("root back") {
        Files.move(tempDir.resolve("lib-away"), root)
        listOf(scan(), state())
      }
      case("new series with same books as deleted one is not restored without hashes") {
        cbz("s3/e.cbz", 5)
        touchDirs(t2)
        listOf(scan(), state())
      }
    }
    func("tryRestoreSeries") {
      case("deleted series without hash") {
        Files.move(root.resolve("s3"), root.resolve("s3b"))
        touchDirs(t1)
        listOf(scan(), state())
      }
    }
    func("tryRestoreBooks") {
      case("moved to other series") {
        Files.move(root.resolve("s1 moved/d2.cbz"), root.resolve("s3b/d2.cbz"))
        touchDirs(t2)
        listOf(scan(), state())
      }
    }
    func("emptyTrash") {
      case("deleted series and books") {
        lifecycle.emptyTrash(lib())
        state()
      }
      case("nothing to delete") {
        lifecycle.emptyTrash(lib())
        state()
      }
      case("empty trash after scan") {
        db.libraryDao.update(lib().copy(emptyTrashAfterScan = true))
        Files.delete(root.resolve("s3b/e.cbz"))
        touchDirs(t1)
        listOf(scan(), state())
      }
    }
    func("cleanupEmptySets") {
      case("empty collections and read lists deleted") {
        db.seriesCollectionDao.insert(SeriesCollection("empty col", id = "C2", createdDate = date))
        db.readListDao.insert(ReadList("empty rl", id = "RL2", createdDate = date))
        db.libraryDao.update(lib().copy(emptyTrashAfterScan = false))
        listOf(scan(), state(), db.rawQuery("select ID from COLLECTION order by ID"), db.rawQuery("select ID from READLIST order by ID"))
      }
      case("root emptied") {
        Files.walk(root).use { s -> s.toList() }.sortedDescending().filter { it != root }.forEach { Files.delete(it) }
        touchDirs(t2)
        listOf(scan(), state())
      }
    }
  }
}
