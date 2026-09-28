package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.HistoricalEventController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.sql
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class HistoricalEventControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val controller = HistoricalEventController(db.historicalEventDtoDao)

  override fun cases() {
    func("getHistoricalEvents") {
      case("empty") { controller.getHistoricalEvents(PageRequest.of(0, 20)) }
      case("default sort is timestamp desc") {
        sql(
          db,
          "INSERT INTO HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) VALUES ('E1', 'BookFileDeleted', 'B1', 'S1', '2020-01-01 10:00:00.0')",
          "INSERT INTO HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) VALUES ('E2', 'SeriesFolderDeleted', NULL, 'S2', '2021-06-01 10:00:00.0')",
          "INSERT INTO HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) VALUES ('E3', 'BookImported', 'B3', 'S1', '2019-12-31 23:59:59.5')",
          "INSERT INTO HISTORICAL_EVENT_PROPERTIES (ID, KEY, VALUE) VALUES ('E1', 'reason', 'gone'), ('E1', 'name', '/a/b.cbz'), ('E3', 'upgrade', 'No')",
        )
        controller.getHistoricalEvents(PageRequest.of(0, 20))
      }
      case("unsorted pageable") { controller.getHistoricalEvents(Pageable.ofSize(2)) }
      case("second page") { controller.getHistoricalEvents(PageRequest.of(1, 2)) }
      case("sort by type asc") { controller.getHistoricalEvents(PageRequest.of(0, 20, Sort.by("type"))) }
      case("sort by seriesId desc then timestamp") {
        controller.getHistoricalEvents(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("seriesId"), Sort.Order.asc("timestamp"))))
      }
      case("page beyond") { controller.getHistoricalEvents(PageRequest.of(5, 2)) }
    }
  }
}
