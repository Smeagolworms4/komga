package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Sidecar
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class SidecarDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.sidecarDao

  private fun sc(
    path: String,
    parent: String = "file:/lib1/series",
    time: LocalDateTime = LocalDateTime.of(2020, 5, 1, 12, 30, 15),
    type: Sidecar.Type = Sidecar.Type.ARTWORK,
    source: Sidecar.Source = Sidecar.Source.SERIES,
  ) = Sidecar(URL(path), URL(parent), time, type, source)

  private fun urls() = dao.findAll().map { it.url.toString() to it.libraryId }.sortedBy { it.first }

  override fun cases() {
    func("findAll") {
      case("empty") { dao.findAll() }
    }

    func("save") {
      case("insert") {
        db.libraryDao.insert(Library("lib1", URL("file:/lib1"), id = "L1"))
        db.libraryDao.insert(Library("lib2", URL("file:/lib2"), id = "L2"))
        dao.save("L1", sc("file:/lib1/series/cover.jpg"))
        dao.findAll()
      }
      case("metadata sidecar with unicode url") {
        dao.save("L1", sc("file:/lib1/s%C3%A9rie/series.json", "file:/lib1/s%C3%A9rie", type = Sidecar.Type.METADATA, source = Sidecar.Source.BOOK))
        dao.findAll().map { it.url }
      }
      case("same url updates time, parent and library") {
        dao.save("L2", sc("file:/lib1/series/cover.jpg", "file:/lib2/other", LocalDateTime.of(2021, 12, 31, 23, 59, 59, 999000000)))
        dao.findAll()
      }
      case("unknown library") { exceptionType { dao.save("NOPE", sc("file:/x")) } }
      case("more sidecars") {
        (1..5).forEach { dao.save(if (it % 2 == 0) "L1" else "L2", sc("file:/lib/s$it/cover.jpg")) }
        urls()
      }
    }

    func("toDomain") {
      case("stored values") { db.rawQuery("select URL, PARENT_URL, LAST_MODIFIED_TIME, LIBRARY_ID from SIDECAR order by URL") }
      case("dates") { dao.findAll().map { it.lastModifiedTime }.sorted() }
    }

    func("countGroupedByLibraryId") {
      case("two libraries") { dao.countGroupedByLibraryId().toSortedMap() }
    }

    func("deleteByLibraryIdAndUrls") {
      case("empty list") {
        dao.deleteByLibraryIdAndUrls("L1", emptyList())
        urls()
      }
      case("url of another library is kept") {
        dao.deleteByLibraryIdAndUrls("L1", listOf(URL("file:/lib/s1/cover.jpg")))
        urls()
      }
      case("some urls") {
        dao.deleteByLibraryIdAndUrls("L2", listOf(URL("file:/lib/s1/cover.jpg"), URL("file:/lib/s3/cover.jpg"), URL("file:/missing")))
        urls()
      }
      case("large list over batch size") {
        val many = (1..2500).map { URL("file:/many/$it") } + URL("file:/lib/s2/cover.jpg")
        dao.deleteByLibraryIdAndUrls("L1", many)
        urls()
      }
    }

    func("deleteByLibraryId") {
      case("existing") {
        dao.deleteByLibraryId("L2")
        urls()
      }
      case("missing") {
        dao.deleteByLibraryId("NOPE")
        dao.countGroupedByLibraryId()
      }
    }

    func("countGroupedByLibraryId") {
      case("after deletes") { dao.countGroupedByLibraryId() }
      case("empty") {
        dao.deleteByLibraryId("L1")
        dao.countGroupedByLibraryId()
      }
    }
  }
}
