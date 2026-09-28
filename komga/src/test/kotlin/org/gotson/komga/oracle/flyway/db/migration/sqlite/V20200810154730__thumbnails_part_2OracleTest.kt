package org.gotson.komga.oracle.flyway.db.migration.sqlite

import db.migration.sqlite.V20200810154730__thumbnails_part_2
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.sql.Connection

/** The migration runs on a database migrated up to the previous version (20200810154729), filled by each case */
class V20200810154730__thumbnails_part_2OracleTest : OracleTest() {
  private val conn = OracleDb.mainConnectionAt("20200810154729")
  private val context =
    object : Context {
      override fun getConfiguration(): Configuration? = null

      override fun getConnection(): Connection = conn
    }

  private fun rows(sql: String) = OracleDb.query(conn, sql)

  override fun cases() {
    func("migrate") {
      case("empty media") {
        V20200810154730__thumbnails_part_2().migrate(context)
        rows("select count(*) from THUMBNAIL_BOOK")
      }
      case("copies thumbnails") {
        OracleDb.exec(
          conn,
          "insert into LIBRARY(ID, NAME, ROOT) values ('L1', 'lib', 'file:/lib')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S1', '2020-01-01 00:00:00', 's1', 'file:/lib/S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B1', '2020-01-01 00:00:00', 'book B1', 'file:/lib/S1/B1.cbz', 'S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B2', '2020-01-01 00:00:00', 'book B2', 'file:/lib/S1/B2.cbz', 'S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B3', '2020-01-01 00:00:00', 'book B3', 'file:/lib/S1/B3.cbz', 'S1', 'L1')",
          "insert into MEDIA(BOOK_ID, STATUS, THUMBNAIL) values ('B1', 'READY', X'0102FF')",
          "insert into MEDIA(BOOK_ID, STATUS, THUMBNAIL) values ('B2', 'ERROR', null)",
          "insert into MEDIA(BOOK_ID, STATUS, THUMBNAIL) values ('B3', 'READY', X'')",
        )
        V20200810154730__thumbnails_part_2().migrate(context)
        stable(rows("select ID, THUMBNAIL, SELECTED, TYPE, BOOK_ID, URL from THUMBNAIL_BOOK order by BOOK_ID"))
      }
      case("ids are distinct") {
        rows("select count(distinct ID), count(*) from THUMBNAIL_BOOK")
      }
    }
  }
}
