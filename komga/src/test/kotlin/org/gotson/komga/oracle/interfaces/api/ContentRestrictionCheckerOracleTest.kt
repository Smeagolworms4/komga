package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.interfaces.api.ContentRestrictionChecker
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData
import java.net.URL

class ContentRestrictionCheckerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val checker = ContentRestrictionChecker(db.seriesMetadataDao, db.bookDao, db.thumbnailBookDao, db.seriesDao, db.thumbnailSeriesDao)
  private val users = listOf(InterfacesData.admin, InterfacesData.limited, InterfacesData.restricted)

  private fun each(
    ids: List<String>,
    check: (KomgaUser, String) -> Unit,
  ) = users.map { u ->
    ids.map { id ->
      try {
        check(u, id)
        "ok"
      } catch (e: Exception) {
        e.message
      }
    }
  }

  private val books = listOf("B1", "B4", "B6", "BX")
  private val series = listOf("S1", "S2", "S3", "SX")

  override fun cases() {
    func("checkContentRestrictionBook@30") {
      case("setup") {
        InterfacesData.setup(db)
        db.thumbnailBookDao.insert(ThumbnailBook(url = URL("file:/t.jpg"), type = ThumbnailBook.Type.SIDECAR, mediaType = "image/jpeg", fileSize = 1, dimension = Dimension(1, 1), id = "TB1", bookId = "B1"))
        db.thumbnailBookDao.insert(ThumbnailBook(url = URL("file:/t.jpg"), type = ThumbnailBook.Type.SIDECAR, mediaType = "image/jpeg", fileSize = 1, dimension = Dimension(1, 1), id = "TB4", bookId = "B4"))
        db.thumbnailSeriesDao.insert(ThumbnailSeries(url = URL("file:/t.jpg"), type = ThumbnailSeries.Type.SIDECAR, mediaType = "image/jpeg", fileSize = 1, dimension = Dimension(1, 1), id = "TS1", seriesId = "S1"))
        db.thumbnailSeriesDao.insert(ThumbnailSeries(url = URL("file:/t.jpg"), type = ThumbnailSeries.Type.SIDECAR, mediaType = "image/jpeg", fileSize = 1, dimension = Dimension(1, 1), id = "TS2", seriesId = "S2"))
      }
      case("book dto") { each(books.dropLast(1)) { u, id -> checker.checkContentRestrictionBook(u, db.bookDtoDao.findByIdOrNull(id, u.id)!!) } }
    }
    func("checkContentRestrictionBook@47") {
      case("book") { each(books.dropLast(1)) { u, id -> checker.checkContentRestrictionBook(u, db.bookDao.findByIdOrNull(id)!!) } }
    }
    func("checkContentRestrictionBook@64") {
      case("book id") { each(books) { u, id -> checker.checkContentRestrictionBook(u, id) } }
    }
    func("checkContentRestrictionBookThumbnail") {
      case("thumbnail id") { each(listOf("TB1", "TB4", "TX")) { u, id -> checker.checkContentRestrictionBookThumbnail(u, id) } }
    }
    func("checkContentRestrictionSeries@108") {
      case("series dto") { each(series.dropLast(1)) { u, id -> checker.checkContentRestrictionSeries(u, db.seriesDtoDao.findByIdOrNull(id, u.id)!!) } }
    }
    func("checkContentRestrictionSeries@121") {
      case("series id") { each(series) { u, id -> checker.checkContentRestrictionSeries(u, id) } }
    }
    func("checkContentRestrictionSeriesThumbnail") {
      case("thumbnail id") { each(listOf("TS1", "TS2", "TX")) { u, id -> checker.checkContentRestrictionSeriesThumbnail(u, id) } }
    }
  }
}
