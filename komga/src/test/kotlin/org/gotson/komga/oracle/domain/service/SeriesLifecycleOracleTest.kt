package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.MarkSelectedPreference
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class SeriesLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.seriesLifecycle

  private val dir by lazy { tempDir.resolve("lib").createDirectories() }

  private val user = KomgaUser("u@example.org", "p", id = "U1", createdDate = date)

  private fun url(rel: String) = URL("file:$dir/$rel")

  private fun books(seriesId: String) = db.bookDao.findAllBySeriesId(seriesId).sortedBy { it.id }

  private fun state(seriesId: String) =
    attempt(dir) {
      val bs = books(seriesId)
      listOf(
        db.seriesDao.findByIdOrNull(seriesId),
        bs.map { listOf(it.id, it.name, it.number, it.deletedDate) },
        db.bookMetadataDao.findAllByIds(bs.map { it.id }).sortedBy { it.bookId }.map { listOf(it.bookId, it.title, it.number, it.numberSort, it.numberLock, it.numberSortLock) },
        graph.takeTasks(),
        graph.takeEvents(),
      )
    }

  private fun sThumb(
    id: String,
    seriesId: String,
    type: ThumbnailSeries.Type = ThumbnailSeries.Type.USER_UPLOADED,
    selected: Boolean = false,
    bytes: ByteArray? = oracleBytes(4),
    url: URL? = null,
  ) = ThumbnailSeries(bytes, url, selected, type, "image/jpeg", 4, Dimension(1, 1), id, seriesId, date)

  private fun thumbs() = attempt(dir) { listOf(db.rawQuery("select ID, SERIES_ID, TYPE, SELECTED, URL from THUMBNAIL_SERIES order by ID"), graph.takeEvents()) }

  private fun sc(v: Any?) = ServiceGraph.scrub(v, dir)

  override fun cases() {
    func("createSeries") {
      case("setup") {
        db.libraryDao.insert(library("L1", url("")))
        db.libraryDao.insert(library("L2"))
        db.komgaUserDao.insert(user)
        true
      }
      case("new series") {
        sc(listOf(lifecycle.createSeries(series("S1", "L1", url("s1"))), db.seriesMetadataDao.findById("S1"), db.bookMetadataAggregationDao.findById("S1"), graph.takeEvents()))
      }
      case("second series") { sc(listOf(lifecycle.createSeries(series("S2", "L1", url("s2"))), graph.takeEvents())) }
      case("oneshot series with long name") {
        sc(lifecycle.createSeries(series("S3", "L1", url("s3")).copy(name = "  Ünïcode — 漫画  ", oneshot = true)))
      }
      case("duplicate id") { exceptionType { lifecycle.createSeries(series("S1", "L1")) } }
      case("unknown library") { exceptionType { lifecycle.createSeries(series("S9", "L9")) } }
      case("after errors") { sc(listOf(db.seriesDao.findAll().map { it.id }, graph.takeEvents())) }
    }
    func("addBooks") {
      case("other library") { lifecycle.addBooks(db.seriesDao.findByIdOrNull("S1")!!, listOf(book("B0", "S1", "L2"))) }
      case("no book") {
        lifecycle.addBooks(db.seriesDao.findByIdOrNull("S1")!!, emptyList())
        state("S1")
      }
      case("books get series id, media and metadata") {
        lifecycle.addBooks(
          db.seriesDao.findByIdOrNull("S1")!!,
          listOf(
            book("B1", "SX", "L1", name = "Book 10", url = url("s1/b1.cbz")),
            book("B2", "", "L1", name = "book 2", url = url("s1/b2.cbz"), number = 7),
            book("B3", "S1", "L1", name = "Book 1", url = url("s1/b3.cbz")),
            book("B4", "S1", "L1", name = "  Böök   3 ", url = url("s1/b4.cbz")),
            book("B5", "S1", "L1", name = "book 2.5", url = url("s1/b5.cbz")),
            book("B6", "S1", "L1", name = "Book\t02", url = url("s1/b6.cbz")),
          ),
        )
        listOf(state("S1"), sc(db.mediaDao.findById("B2")))
      }
      case("duplicate book") { exceptionType { lifecycle.addBooks(db.seriesDao.findByIdOrNull("S1")!!, listOf(book("B1", "S1", "L1"))) } }
    }
    func("sortBooks") {
      case("natural sort") {
        lifecycle.sortBooks(db.seriesDao.findByIdOrNull("S1")!!)
        state("S1")
      }
      case("sorted again, nothing changes") {
        lifecycle.sortBooks(db.seriesDao.findByIdOrNull("S1")!!)
        state("S1")
      }
      case("locked metadata") {
        val ms = db.bookMetadataDao.findAllByIds(listOf("B1", "B2", "B3")).associateBy { it.bookId }
        db.bookMetadataDao.update(
          listOf(
            ms.getValue("B1").copy(number = "x", numberLock = true),
            ms.getValue("B2").copy(numberSort = 99F, numberSortLock = true),
            ms.getValue("B3").copy(number = "y", numberSort = 42F, numberLock = true, numberSortLock = true),
          ),
        )
        db.bookDao.update(db.bookDao.findByIdOrNull("B5")!!.copy(name = "Book 0"))
        lifecycle.sortBooks(db.seriesDao.findByIdOrNull("S1")!!)
        state("S1")
      }
      case("empty series") {
        lifecycle.sortBooks(db.seriesDao.findByIdOrNull("S2")!!)
        state("S2")
      }
      case("unknown series") {
        lifecycle.sortBooks(series("S9", "L1"))
        state("S9")
      }
    }
    func("markReadProgressCompleted") {
      case("setup media and progress") {
        listOf("B1", "B2", "B3", "B4", "B5", "B6").forEachIndexed { i, id ->
          db.mediaDao.update(Media(status = Media.Status.READY, mediaType = "application/zip", pages = List(i + 1) { BookPage("p$it.jpg", "image/jpeg") }, bookId = id, createdDate = date))
        }
        db.readProgressDao.save(ReadProgress("B1", "U1", 1, true, date, createdDate = date))
        db.readProgressDao.save(ReadProgress("B2", "U1", 1, false, date, createdDate = date))
        db.readProgressDao.findAll().size
      }
      case("marks unread and in progress books") {
        lifecycle.markReadProgressCompleted("S1", user)
        sc(listOf(db.readProgressDao.findAllByUserId("U1").sortedBy { it.bookId }.map { listOf(it.bookId, it.page, it.completed, it.readDate == date) }, graph.takeEvents().map { it.javaClass.simpleName }))
      }
      case("again") {
        lifecycle.markReadProgressCompleted("S1", user)
        sc(graph.takeEvents())
      }
      case("empty series") {
        lifecycle.markReadProgressCompleted("S2", user)
        sc(graph.takeEvents())
      }
      case("unknown series") {
        lifecycle.markReadProgressCompleted("S9", user)
        sc(graph.takeEvents())
      }
    }
    func("deleteReadProgress") {
      case("series with progress") {
        db.komgaUserDao.insert(KomgaUser("u2@example.org", "p", id = "U2", createdDate = date))
        db.readProgressDao.save(ReadProgress("B1", "U2", 1, true, date, createdDate = date))
        lifecycle.deleteReadProgress("S1", user)
        sc(listOf(db.readProgressDao.findAll().map { listOf(it.bookId, it.userId) }, graph.takeEvents().map { it.javaClass.simpleName }))
      }
      case("again") {
        lifecycle.deleteReadProgress("S1", user)
        sc(graph.takeEvents())
      }
      case("unknown series") {
        lifecycle.deleteReadProgress("S9", user)
        sc(graph.takeEvents())
      }
    }
    func("addThumbnailForSeries") {
      case("uploaded, IF_NONE_OR_GENERATED, none yet") { attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T1", "S1"), MarkSelectedPreference.IF_NONE_OR_GENERATED) } }
      case("state 1") { thumbs() }
      case("uploaded, IF_NONE_OR_GENERATED, one selected") { attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T2", "S1"), MarkSelectedPreference.IF_NONE_OR_GENERATED) } }
      case("state 2") { thumbs() }
      case("uploaded, YES") { attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T3", "S1"), MarkSelectedPreference.YES) } }
      case("uploaded, NO") { attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T4", "S1", selected = true), MarkSelectedPreference.NO) } }
      case("state 3") { thumbs() }
      case("sidecar with url") {
        dir.resolve("s1").createDirectories().resolve("cover.jpg").writeBytes(oracleBytes(16))
        attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T5", "S1", ThumbnailSeries.Type.SIDECAR, bytes = null, url = url("s1/cover.jpg")), MarkSelectedPreference.NO) }
      }
      case("sidecar with same url replaces") {
        attempt(dir) { lifecycle.addThumbnailForSeries(sThumb("T6", "S1", ThumbnailSeries.Type.SIDECAR, bytes = null, url = url("s1/cover.jpg")), MarkSelectedPreference.YES) }
      }
      case("state 4") { thumbs() }
      case("unknown series") { exceptionType { lifecycle.addThumbnailForSeries(sThumb("T7", "S9"), MarkSelectedPreference.YES) } }
    }
    func("getThumbnailBytesByThumbnailId") {
      case("bytes") { lifecycle.getThumbnailBytesByThumbnailId("T1") }
      case("url") { lifecycle.getThumbnailBytesByThumbnailId("T6") }
      case("unknown") { lifecycle.getThumbnailBytesByThumbnailId("T9") }
    }
    func("getBytesFromThumbnailSeries") {
      case("url file missing") {
        db.thumbnailSeriesDao.insert(sThumb("T8", "S2", ThumbnailSeries.Type.SIDECAR, bytes = null, url = url("s2/missing.jpg")))
        attempt(dir) { lifecycle.getThumbnailBytesByThumbnailId("T8") }
      }
      case("no bytes nor url") {
        db.thumbnailSeriesDao.insert(sThumb("T9", "S2", bytes = null))
        lifecycle.getThumbnailBytesByThumbnailId("T9")
      }
    }
    func("getSelectedThumbnail") {
      case("selected sidecar exists") { attempt(dir) { lifecycle.getSelectedThumbnail("S1") } }
      case("selected sidecar deleted") {
        Files.delete(dir.resolve("s1/cover.jpg"))
        attempt(dir) { listOf(lifecycle.getSelectedThumbnail("S1"), db.rawQuery("select ID, SELECTED from THUMBNAIL_SERIES where SERIES_ID = 'S1' order by ID")) }
      }
      case("nothing selected, missing sidecar removed") {
        attempt(dir) { listOf(lifecycle.getSelectedThumbnail("S2"), db.rawQuery("select ID, SELECTED from THUMBNAIL_SERIES where SERIES_ID = 'S2' order by ID")) }
      }
      case("unknown series") { lifecycle.getSelectedThumbnail("S9") }
    }
    func("thumbnailsHouseKeeping") {
      case("several selected") {
        db.thumbnailSeriesDao.insert(sThumb("TA", "S3", selected = true))
        db.thumbnailSeriesDao.insert(sThumb("TB", "S3", selected = true))
        db.thumbnailSeriesDao.insert(sThumb("TC", "S3", ThumbnailSeries.Type.SIDECAR, selected = true, bytes = null, url = url("s3/missing.jpg")))
        db.thumbnailSeriesDao.markSelected(sThumb("TC", "S3"))
        attempt(dir) { listOf(lifecycle.getSelectedThumbnail("S3"), db.rawQuery("select ID, SELECTED from THUMBNAIL_SERIES where SERIES_ID = 'S3' order by ID")) }
      }
    }
    func("getThumbnailBytes") {
      case("selected series thumbnail") { lifecycle.getThumbnailBytes("S3", "U1") }
      case("unknown series") { lifecycle.getThumbnailBytes("S9", "U1") }
      case("series without thumbnail nor book") { lifecycle.getThumbnailBytes("S2", "U1") }
      for (cover in Library.SeriesCover.entries) {
        case("book cover $cover") {
          if (cover == Library.SeriesCover.entries.first()) {
            db.thumbnailSeriesDao.deleteBySeriesId("S1")
            books("S1").forEachIndexed { i, b ->
              db.thumbnailBookDao.insert(ThumbnailBook(oracleBytes(i + 1), null, true, ThumbnailBook.Type.GENERATED, "image/jpeg", i + 1L, Dimension(1, 1), "TB${b.id}", b.id, date))
            }
            db.readProgressDao.save(ReadProgress("B3", "U1", 1, true, date, createdDate = date))
            db.readProgressDao.save(ReadProgress("B5", "U1", 1, true, date, createdDate = date))
          }
          db.libraryDao.update(db.libraryDao.findById("L1").copy(seriesCover = cover))
          listOf(lifecycle.getThumbnailBytes("S1", "U1"), lifecycle.getThumbnailBytes("S1", "U2"))
        }
      }
      case("all read, first unread or last") {
        books("S1").forEach { db.readProgressDao.save(ReadProgress(it.id, "U1", 1, true, date, createdDate = date)) }
        db.libraryDao.update(db.libraryDao.findById("L1").copy(seriesCover = Library.SeriesCover.FIRST_UNREAD_OR_LAST))
        lifecycle.getThumbnailBytes("S1", "U1")
      }
      case("all read, first unread or first") {
        db.libraryDao.update(db.libraryDao.findById("L1").copy(seriesCover = Library.SeriesCover.FIRST_UNREAD_OR_FIRST))
        lifecycle.getThumbnailBytes("S1", "U1")
      }
    }
    func("deleteThumbnailForSeries") {
      case("sidecar refused") { attempt(dir) { lifecycle.deleteThumbnailForSeries(sThumb("TC", "S3", ThumbnailSeries.Type.SIDECAR)) } }
      case("uploaded") {
        lifecycle.deleteThumbnailForSeries(sThumb("TA", "S3"))
        thumbs()
      }
      case("unknown uploaded") {
        lifecycle.deleteThumbnailForSeries(sThumb("TZ", "S3"))
        thumbs()
      }
    }
    func("softDeleteMany") {
      case("two series") {
        lifecycle.softDeleteMany(listOf(db.seriesDao.findByIdOrNull("S2")!!, db.seriesDao.findByIdOrNull("S3")!!))
        sc(listOf(db.seriesDao.findAll().sortedBy { it.id }.map { listOf(it.id, it.deletedDate) }, graph.takeEvents()))
      }
      case("empty") {
        lifecycle.softDeleteMany(emptyList())
        sc(graph.takeEvents())
      }
    }
    func("deleteSeriesFiles") {
      case("folder does not exist") {
        lifecycle.deleteSeriesFiles(db.seriesDao.findByIdOrNull("S2")!!)
        sc(listOf(db.seriesDao.findByIdOrNull("S2")!!.deletedDate, graph.takeEvents()))
      }
      case("folder with books and sidecar") {
        val s1 = dir.resolve("s1").createDirectories()
        listOf("b1.cbz", "b2.cbz", "b3.cbz", "b4.cbz", "b5.cbz", "b6.cbz", "cover.jpg").forEach { s1.resolve(it).writeBytes(oracleBytes(3)) }
        db.thumbnailSeriesDao.insert(sThumb("TS1", "S1", ThumbnailSeries.Type.SIDECAR, bytes = null, url = url("s1/cover.jpg")))
        attempt(dir) {
          lifecycle.deleteSeriesFiles(db.seriesDao.findByIdOrNull("S1")!!)
          listOf(
            Files.exists(s1),
            books("S1").map { listOf(it.id, it.deletedDate) },
            db.rawQuery("select TYPE, BOOK_ID, SERIES_ID from HISTORICAL_EVENT order by TYPE, BOOK_ID"),
            db.seriesDao.findByIdOrNull("S1")!!.deletedDate,
            graph.takeEvents().map { it.javaClass.simpleName },
          )
        }
      }
      case("folder with other files is kept") {
        val s4 = dir.resolve("s4").createDirectories()
        s4.resolve("notes.txt").writeBytes(oracleBytes(3))
        lifecycle.createSeries(series("S4", "L1", url("s4")))
        attempt(dir) {
          lifecycle.deleteSeriesFiles(db.seriesDao.findByIdOrNull("S4")!!)
          listOf(Files.exists(s4), db.seriesDao.findByIdOrNull("S4")!!.deletedDate, graph.takeEvents().map { it.javaClass.simpleName })
        }
      }
      case("empty folder") {
        val s5 = dir.resolve("s5").createDirectories()
        lifecycle.createSeries(series("S5", "L1", url("s5")))
        attempt(dir) {
          lifecycle.deleteSeriesFiles(db.seriesDao.findByIdOrNull("S5")!!)
          listOf(Files.exists(s5), db.rawQuery("select TYPE, SERIES_ID from HISTORICAL_EVENT where SERIES_ID = 'S5'"), graph.takeEvents().map { it.javaClass.simpleName })
        }
      }
    }
    func("deleteMany") {
      case("series with related data") {
        db.readProgressDao.save(ReadProgress("B1", "U1", 1, true, date, createdDate = date))
        db.seriesCollectionDao.insert(SeriesCollection("col", seriesIds = listOf("S1", "S2"), id = "C1", createdDate = date))
        db.thumbnailSeriesDao.insert(sThumb("TD", "S1"))
        lifecycle.deleteMany(listOf(db.seriesDao.findByIdOrNull("S1")!!, db.seriesDao.findByIdOrNull("S3")!!))
        sc(
          listOf(
            db.seriesDao.findAll().map { it.id }.sorted(),
            db.bookDao.findAll().map { it.id },
            db.rawQuery("select count(*) from READ_PROGRESS"),
            db.rawQuery("select SERIES_ID from COLLECTION_SERIES"),
            db.rawQuery("select ID from THUMBNAIL_SERIES order by ID"),
            db.rawQuery("select SERIES_ID from SERIES_METADATA order by SERIES_ID"),
            db.rawQuery("select SERIES_ID from BOOK_METADATA_AGGREGATION order by SERIES_ID"),
            db.rawQuery("select count(*) from MEDIA"),
            graph.takeEvents().map { it.javaClass.simpleName },
          ),
        )
      }
      case("empty") {
        lifecycle.deleteMany(emptyList())
        sc(graph.takeEvents())
      }
    }
  }
}
