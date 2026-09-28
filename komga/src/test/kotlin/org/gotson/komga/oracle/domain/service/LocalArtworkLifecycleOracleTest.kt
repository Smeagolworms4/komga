package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.service.LocalArtworkLifecycle
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import java.net.URL
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class LocalArtworkLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = LocalArtworkLifecycle(db.libraryDao, graph.bookLifecycle, graph.seriesLifecycle, graph.localArtworkProvider)

  private val dir by lazy { tempDir.resolve("lib").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun url(rel: String) = URL("file:$dir/$rel")

  private fun bookState(id: String) =
    attempt(dir) {
      listOf(
        db.thumbnailBookDao.findAllByBookId(id).sortedBy { it.url.toString() }.map { listOf(it.type, it.selected, it.url, it.mediaType, it.fileSize, it.dimension) },
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  private fun seriesState(id: String) =
    attempt(dir) {
      listOf(
        db.thumbnailSeriesDao.findAllBySeriesId(id).sortedBy { it.url.toString() }.map { listOf(it.type, it.selected, it.url, it.mediaType, it.fileSize, it.dimension) },
        graph.takeEvents().map { it.javaClass.simpleName },
      )
    }

  override fun cases() {
    func("refreshLocalArtwork@20") {
      case("setup") {
        db.libraryDao.insert(library("L1", url("")))
        db.libraryDao.insert(library("L2", url("")).copy(importLocalArtwork = false))
        val s1 = dir.resolve("s1").createDirectories()
        val s2 = dir.resolve("s2").createDirectories()
        s1.resolve("cover.png").writeBytes(png)
        s1.resolve("notes.txt").writeBytes(png)
        s1.resolve("b1.png").writeBytes(png)
        s1.resolve("B2-1.PNG").writeBytes(png)
        s1.resolve("b3.jpg").writeBytes(oracleBytes(10))
        s1.resolve("b10.png").writeBytes(png)
        s2.resolve("folder.png").writeBytes(png)
        db.seriesDao.insert(series("S1", "L1", url("s1")))
        db.seriesDao.insert(series("S2", "L2", url("s2")))
        db.seriesDao.insert(series("S3", "L1", url("s2")).copy(oneshot = true))
        listOf("b1", "b2", "b3", "b4").forEach { db.bookDao.insert(book(it.uppercase(), "S1", "L1", url = url("s1/$it.cbz"))) }
        db.bookDao.insert(book("B5", "S2", "L2", url = url("s2/folder.cbz")))
        true
      }
      case("exact name") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B1")!!)
        bookState("B1")
      }
      case("again, same url replaced") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B1")!!)
        bookState("B1")
      }
      case("numbered, other case") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B2")!!)
        bookState("B2")
      }
      case("not an image") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B3")!!)
        bookState("B3")
      }
      case("no sidecar") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B4")!!)
        bookState("B4")
      }
      case("selected uploaded thumbnail kept") {
        db.thumbnailBookDao.insert(ThumbnailBook(oracleBytes(4), null, true, ThumbnailBook.Type.USER_UPLOADED, "image/jpeg", 4, Dimension(1, 1), "TU", "B2", date))
        db.thumbnailBookDao.markSelected(ThumbnailBook(oracleBytes(4), null, true, ThumbnailBook.Type.USER_UPLOADED, "image/jpeg", 4, Dimension(1, 1), "TU", "B2", date))
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B2")!!)
        bookState("B2")
      }
      case("library import disabled") {
        lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B5")!!)
        bookState("B5")
      }
      case("unknown library") { exceptionType { lifecycle.refreshLocalArtwork(db.bookDao.findByIdOrNull("B1")!!.copy(libraryId = "L9")) } }
      case("missing folder") { attempt(dir) { lifecycle.refreshLocalArtwork(book("B9", "S1", "L1", url = url("nope/b9.cbz"))) } }
    }
    func("refreshLocalArtwork@32") {
      case("cover file") {
        lifecycle.refreshLocalArtwork(db.seriesDao.findByIdOrNull("S1")!!)
        seriesState("S1")
      }
      case("again") {
        lifecycle.refreshLocalArtwork(db.seriesDao.findByIdOrNull("S1")!!)
        seriesState("S1")
      }
      case("library import disabled") {
        lifecycle.refreshLocalArtwork(db.seriesDao.findByIdOrNull("S2")!!)
        seriesState("S2")
      }
      case("oneshot") {
        lifecycle.refreshLocalArtwork(db.seriesDao.findByIdOrNull("S3")!!)
        seriesState("S3")
      }
      case("missing folder") { attempt(dir) { lifecycle.refreshLocalArtwork(series("S9", "L1", url("nope"))) } }
    }
  }
}
