package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.domain.service.PageHashLifecycle
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
import java.net.URL
import kotlin.io.path.createDirectories

class PageHashLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = PageHashLifecycle(db.pageHashDao, db.mediaDao, graph.bookLifecycle, db.bookDao, db.properties)

  private val dir by lazy { tempDir.resolve("books").createDirectories() }
  private val png by lazy { resource("barcode/komga.png") }

  private fun addBook(
    id: String,
    seriesId: String,
    libraryId: String,
    hashes: List<String>,
    status: Media.Status = Media.Status.READY,
    mediaType: String = "application/zip",
    url: URL? = null,
  ) {
    val u = url ?: zipFile(dir, "$id.cbz", hashes.indices.map { "p$it.png" to png })
    val b = book(id, seriesId, libraryId, url = u)
    db.bookDao.insert(b)
    db.bookMetadataDao.insert(metadata(b))
    db.mediaDao.insert(
      Media(
        status = status,
        mediaType = mediaType,
        pages = hashes.mapIndexed { i, h -> BookPage("p$i.png", "image/png", fileHash = h, fileSize = png.size.toLong()) },
        bookId = id,
        createdDate = date,
      ),
    )
  }

  private fun lib(
    id: String,
    hashPages: Boolean,
  ) = db.libraryDao.findById(id).copy(hashPages = hashPages)

  private fun typed(t: org.gotson.komga.domain.model.TypedBytes?) = t?.let { listOf(it.bytes.size, it.mediaType) }

  override fun cases() {
    func("getBookIdsWithMissingPageHash") {
      case("setup") {
        db.libraryDao.insert(library("L1"))
        db.libraryDao.insert(library("L2").copy(hashPages = true))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesDao.insert(series("S2", "L2"))
        addBook("B1", "S1", "L1", listOf("H1", ""))
        addBook("B2", "S2", "L2", listOf("", ""))
        addBook("B3", "S2", "L2", listOf("H1", "H2"))
        addBook("B4", "S2", "L2", listOf("", ""), mediaType = "application/epub+zip")
        addBook("B5", "S2", "L2", listOf("", ""), status = Media.Status.UNKNOWN)
        addBook("B6", "S2", "L2", emptyList())
        addBook("B7", "S2", "L2", listOf("H3", "H3", "H3", "H3", "H3", "H3", "", ""))
        addBook("B8", "S2", "L2", listOf("H3", "H3", "H3", "H3", "H3", "", "", ""))
        addBook("B9", "S2", "L2", listOf("H2"), url = URL("file:$dir/missing.cbz"))
        db.bookDao.count()
      }
      case("hash pages disabled") { lifecycle.getBookIdsWithMissingPageHash(lib("L1", false)) }
      case("hash pages enabled") { lifecycle.getBookIdsWithMissingPageHash(lib("L2", true)).sorted() }
      case("enabled on library L1") { lifecycle.getBookIdsWithMissingPageHash(lib("L1", true)).sorted() }
      case("disabled on library L2") { lifecycle.getBookIdsWithMissingPageHash(lib("L2", false)) }
      case("unknown library") { lifecycle.getBookIdsWithMissingPageHash(library("L9").copy(hashPages = true)) }
    }
    func("getPage") {
      case("unknown hash") { lifecycle.getPage("NOPE") }
      case("known hash, original") { attempt(dir) { typed(lifecycle.getPage("H1")) } }
      case("known hash, resized") { attempt(dir) { lifecycle.getPage("H2", 50)?.let { listOf(it.mediaType, graph.describeImage(it.bytes)) } } }
      case("hash of several pages") { attempt(dir) { typed(lifecycle.getPage("H3", null)) } }
      case("empty hash") { attempt(dir) { typed(lifecycle.getPage("")) } }
    }
    func("createOrUpdate") {
      case("new hash with matches") {
        attempt(dir) {
          lifecycle.createOrUpdate(PageHashKnown("H1", 3108, PageHashKnown.Action.IGNORE, createdDate = date))
          listOf(db.pageHashDao.findKnown("H1"), graph.describeImage(db.pageHashDao.getKnownThumbnail("H1")))
        }
      }
      case("new hash without match") {
        attempt(dir) {
          lifecycle.createOrUpdate(PageHashKnown("HX", null, PageHashKnown.Action.DELETE_MANUAL, createdDate = date))
          listOf(db.pageHashDao.findKnown("HX"), db.pageHashDao.getKnownThumbnail("HX"))
        }
      }
      case("existing hash, action updated, size ignored") {
        attempt(dir) {
          lifecycle.createOrUpdate(PageHashKnown("H1", 1, PageHashKnown.Action.DELETE_AUTO, deleteCount = 5, createdDate = date))
          listOf(db.pageHashDao.findKnown("H1"), graph.describeImage(db.pageHashDao.getKnownThumbnail("H1")))
        }
      }
      case("new hash, book file missing") {
        attempt(dir) {
          db.mediaDao.update(db.mediaDao.findById("B3").let { it.copy(pages = it.pages.map { p -> p.copy(fileHash = "H9") }) })
          db.mediaDao.update(db.mediaDao.findById("B9").let { it.copy(pages = it.pages.map { p -> p.copy(fileHash = "H9") }) })
          lifecycle.createOrUpdate(PageHashKnown("H9", 3108, PageHashKnown.Action.DELETE_AUTO, createdDate = date))
          db.pageHashDao.findKnown("H9")
        }
      }
      case("new hash H3 delete auto") {
        attempt(dir) {
          lifecycle.createOrUpdate(PageHashKnown("H3", 3108, PageHashKnown.Action.DELETE_AUTO, createdDate = date))
          db.pageHashDao.findKnown("H3")
        }
      }
    }
    func("getBookPagesToDeleteAutomatically") {
      case("library L1") { lifecycle.getBookPagesToDeleteAutomatically(lib("L1", false)) }
      case("library L2") { lifecycle.getBookPagesToDeleteAutomatically(lib("L2", true)) }
      case("unknown library") { lifecycle.getBookPagesToDeleteAutomatically(library("L9")) }
    }
  }
}
