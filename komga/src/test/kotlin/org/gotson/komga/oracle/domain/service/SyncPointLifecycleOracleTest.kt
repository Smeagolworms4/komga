package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.service.SyncPointLifecycle
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class SyncPointLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val lifecycle = SyncPointLifecycle(db.syncPointDao)

  private val u1 = KomgaUser("u1@example.org", "p", id = "U1", createdDate = date)
  private val u2 = KomgaUser("u2@example.org", "p", sharedAllLibraries = false, sharedLibrariesIds = setOf("L1"), id = "U2", createdDate = date)

  private val ids = mutableMapOf<String, String>()

  private fun addBook(
    id: String,
    seriesId: String,
    libraryId: String,
    number: Int,
    status: Media.Status = Media.Status.READY,
    mediaType: String = "application/epub+zip",
  ) {
    val b = book(id, seriesId, libraryId, number = number).copy(fileSize = 1000L + number, fileHash = "h$id")
    db.bookDao.insert(b)
    db.mediaDao.insert(Media(status = status, mediaType = mediaType, bookId = id, createdDate = date))
    db.bookMetadataDao.insert(metadata(b))
  }

  private fun sp(name: String) = ids.getValue(name)

  override fun cases() {
    func("createSyncPoint") {
      case("setup") {
        db.libraryDao.insert(library("L1"))
        db.libraryDao.insert(library("L2"))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "S1", seriesId = "S1", createdDate = date))
        db.seriesDao.insert(series("S2", "L2"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "S2", seriesId = "S2", createdDate = date))
        addBook("B1", "S1", "L1", 1)
        addBook("B2", "S1", "L1", 2)
        addBook("B3", "S2", "L2", 1)
        addBook("B4", "S1", "L1", 3, status = Media.Status.UNKNOWN)
        addBook("B5", "S1", "L1", 4, mediaType = "application/zip")
        db.komgaUserDao.insert(u1)
        db.komgaUserDao.insert(u2)
        db.readListDao.insert(ReadList(name = "RL1", bookIds = sortedMapOf(1 to "B1", 2 to "B3"), id = "RL1", createdDate = date))
        db.readListDao.insert(ReadList(name = "RL2", bookIds = sortedMapOf(1 to "B4"), id = "RL2", createdDate = date))
        db.readListDao.insert(ReadList(name = "RL3", bookIds = sortedMapOf(1 to "B2"), id = "RL3", createdDate = date))
        db.bookDao.count()
      }
      case("all libraries") { lifecycle.createSyncPoint(u1, null, null).also { ids["all"] = it.id }.let { stable(it) } }
      case("one library") { lifecycle.createSyncPoint(u1, null, listOf("L1")).also { ids["L1"] = it.id }.let { stable(it) } }
      case("empty library list") { lifecycle.createSyncPoint(u1, null, emptyList()).also { ids["none"] = it.id }.let { stable(it) } }
      case("restricted user") { lifecycle.createSyncPoint(u2, null, null).also { ids["u2"] = it.id }.let { stable(it) } }
      case("unknown api key") { exceptionType { lifecycle.createSyncPoint(u1, "APIKEY", null) } }
      case("unknown user") { exceptionType { lifecycle.createSyncPoint(u1.copy(id = "U9"), null, null) } }
    }
    func("takeBooks") {
      case("all libraries, unpaged") { stable(lifecycle.takeBooks(sp("all"), Pageable.unpaged())) }
      case("all libraries, again") { stable(lifecycle.takeBooks(sp("all"), Pageable.unpaged())) }
      case("one library, page of 1") { stable(lifecycle.takeBooks(sp("L1"), PageRequest.of(0, 1))) }
      case("one library, next page of 1") { stable(lifecycle.takeBooks(sp("L1"), PageRequest.of(0, 1))) }
      case("one library, rest") { stable(lifecycle.takeBooks(sp("L1"), Pageable.unpaged())) }
      case("empty library list") { stable(lifecycle.takeBooks(sp("none"), Pageable.unpaged())) }
      case("restricted user") { stable(lifecycle.takeBooks(sp("u2"), Pageable.unpaged())) }
      case("unknown sync point") { stable(lifecycle.takeBooks("NOPE", Pageable.unpaged())) }
    }
    func("takeReadLists") {
      case("all libraries") { stable(lifecycle.takeReadLists(sp("all"), Pageable.unpaged())) }
      case("all libraries, again") { stable(lifecycle.takeReadLists(sp("all"), Pageable.unpaged())) }
      case("one library, page of 1") { stable(lifecycle.takeReadLists(sp("L1"), PageRequest.of(0, 1))) }
      case("restricted user") { stable(lifecycle.takeReadLists(sp("u2"), Pageable.unpaged())) }
    }
    func("takeBooksAdded") {
      case("changes then new sync point") {
        // changed: B1 hash, removed: B2 soft-deleted, added: B6, read progress on B3
        db.bookDao.update(db.bookDao.findByIdOrNull("B1")!!.copy(fileHash = "changed"))
        db.bookDao.update(db.bookDao.findByIdOrNull("B2")!!.copy(deletedDate = date))
        addBook("B6", "S2", "L2", 2)
        db.readProgressDao.save(ReadProgress("B3", "U1", 5, false, date))
        db.readListDao.update(ReadList(name = "RL1 renamed", bookIds = sortedMapOf(1 to "B1", 2 to "B3"), id = "RL1", createdDate = date))
        db.readListDao.delete("RL3")
        db.readListDao.insert(ReadList(name = "RL4", bookIds = sortedMapOf(1 to "B6"), id = "RL4", createdDate = date))
        ids["all2"] = lifecycle.createSyncPoint(u1, null, null).id
        ids["L12"] = lifecycle.createSyncPoint(u1, null, listOf("L1")).id
        true
      }
      case("all") { stable(lifecycle.takeBooksAdded(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeBooksAdded(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeBooksAdded(sp("L1"), sp("L12"), Pageable.unpaged())) }
      case("same sync point") { stable(lifecycle.takeBooksAdded(sp("all"), sp("all"), Pageable.unpaged())) }
    }
    func("takeBooksChanged") {
      case("all") { stable(lifecycle.takeBooksChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeBooksChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library, page of 1") { stable(lifecycle.takeBooksChanged(sp("L1"), sp("L12"), PageRequest.of(0, 1))) }
    }
    func("takeBooksRemoved") {
      case("all") { stable(lifecycle.takeBooksRemoved(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeBooksRemoved(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeBooksRemoved(sp("L1"), sp("L12"), Pageable.unpaged())) }
      case("reverse order") { stable(lifecycle.takeBooksRemoved(sp("all2"), sp("all"), Pageable.unpaged())) }
    }
    func("takeBooksReadProgressChanged") {
      case("all") { stable(lifecycle.takeBooksReadProgressChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeBooksReadProgressChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeBooksReadProgressChanged(sp("L1"), sp("L12"), Pageable.unpaged())) }
    }
    func("takeReadListsAdded") {
      case("all") { stable(lifecycle.takeReadListsAdded(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeReadListsAdded(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeReadListsAdded(sp("L1"), sp("L12"), Pageable.unpaged())) }
    }
    func("takeReadListsChanged") {
      case("all") { stable(lifecycle.takeReadListsChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeReadListsChanged(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeReadListsChanged(sp("L1"), sp("L12"), Pageable.unpaged())) }
    }
    func("takeReadListsRemoved") {
      case("all") { stable(lifecycle.takeReadListsRemoved(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("all, again") { stable(lifecycle.takeReadListsRemoved(sp("all"), sp("all2"), Pageable.unpaged())) }
      case("one library") { stable(lifecycle.takeReadListsRemoved(sp("L1"), sp("L12"), Pageable.unpaged())) }
    }
  }
}
