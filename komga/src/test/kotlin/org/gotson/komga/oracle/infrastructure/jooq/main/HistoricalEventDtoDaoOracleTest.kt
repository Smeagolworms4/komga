package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class HistoricalEventDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.historicalEventDtoDao

  override fun cases() {
    func("findAll") {
      case("empty") { dao.findAll(Pageable.unpaged()) }
      case("unpaged sorted by timestamp") {
        listOf(
          "insert into HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) values ('E1', 'BookFileDeleted', 'B1', 'S1', '2021-01-01 10:00:00')",
          "insert into HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) values ('E2', 'SeriesFolderDeleted', null, 'S2', '2021-03-01 10:00:00.123')",
          "insert into HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) values ('E3', 'BookConverted', 'B3', 'S1', '2020-12-31 23:59:59')",
          "insert into HISTORICAL_EVENT (ID, TYPE, BOOK_ID, SERIES_ID, TIMESTAMP) values ('E4', 'DuplicatePageDeleted', 'B4', null, '2021-02-01 00:00:00')",
          "insert into HISTORICAL_EVENT_PROPERTIES (ID, KEY, VALUE) values ('E1', 'reason', 'Deleted')",
          "insert into HISTORICAL_EVENT_PROPERTIES (ID, KEY, VALUE) values ('E1', 'name', '/lib1/Batman 001.cbz')",
          "insert into HISTORICAL_EVENT_PROPERTIES (ID, KEY, VALUE) values ('E3', 'former file', 'é.cbr')",
          "insert into HISTORICAL_EVENT_PROPERTIES (ID, KEY, VALUE) values ('E4', 'page number', '3')",
        ).forEach { db.dsl.execute(it) }
        dao.findAll(Pageable.unpaged(Sort.by("timestamp")))
      }
      case("paged descending") { dao.findAll(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "timestamp"))) }
      case("sorted by type") { dao.findAll(PageRequest.of(0, 10, Sort.by("type"))).content.map { it.id } }
      case("sorted by book then series") { dao.findAll(PageRequest.of(0, 10, Sort.by(Sort.Order.desc("bookId"), Sort.Order.asc("seriesId")))).content.map { it.id } }
      case("unknown sort") { dao.findAll(PageRequest.of(0, 1, Sort.by("nope"))).let { listOf(it.totalElements, it.size, it.sort.isSorted) } }
      case("unpaged unsorted") { dao.findAll(Pageable.unpaged()).let { listOf(it.content.map { e -> e.id }.sorted(), it.totalElements, it.size) } }
    }
  }
}
