package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.HistoricalEvent
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.book
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.series
import java.nio.file.Paths

class HistoricalEventDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.historicalEventDao

  private fun events() = stable(db.rawQuery("select ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP is not null from HISTORICAL_EVENT order by TYPE, BOOK_ID, SERIES_ID"))

  private fun properties() =
    db.rawQuery(
      "select e.TYPE, p.KEY, p.VALUE from HISTORICAL_EVENT_PROPERTIES p join HISTORICAL_EVENT e on e.ID = p.ID order by e.TYPE, p.KEY, p.VALUE",
    )

  override fun cases() {
    func("insert") {
      case("book file deleted") {
        dao.insert(HistoricalEvent.BookFileDeleted(book("B1", "S1", "L1"), "File was removed"))
        listOf(events(), properties())
      }
      case("series folder deleted with unicode path") {
        dao.insert(HistoricalEvent.SeriesFolderDeleted("S2", Paths.get("/données/漫画 série"), "Folder was removed"))
        listOf(events(), properties())
      }
      case("series folder deleted from series") {
        dao.insert(HistoricalEvent.SeriesFolderDeleted(series("S3", "L1", "Ünïcode"), ""))
        properties()
      }
      case("book imported") {
        dao.insert(HistoricalEvent.BookImported(book("B2", "S1", "L1"), series("S1", "L1"), Paths.get("/import/B2.cbz"), true))
        dao.insert(HistoricalEvent.BookImported(book("B3", "S1", "L1", ext = "epub"), series("S1", "L1"), Paths.get("/import/B3.epub"), false))
        listOf(events(), properties())
      }
      case("book converted") {
        dao.insert(HistoricalEvent.BookConverted(book("B4", "S1", "L1", ext = "cbz"), book("B4", "S1", "L1", ext = "zip")))
        properties().filter { it[0] == "BookConverted" }
      }
      case("same event twice") {
        val event = HistoricalEvent.BookFileDeleted(book("B9", "S9", "L9"), "twice")
        dao.insert(event)
        exceptionType { dao.insert(event) }
      }
      case("counts") { db.rawQuery("select (select count(*) from HISTORICAL_EVENT), (select count(*) from HISTORICAL_EVENT_PROPERTIES)") }
    }
  }
}
