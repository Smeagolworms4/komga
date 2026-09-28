package org.gotson.komga.oracle.flyway.db.migration.sqlite

import db.migration.sqlite.V20210624165023__missing_series_metadata
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.sql.Connection

/** The migration runs on a database migrated up to the previous version (20210617114814), filled by each case */
class V20210624165023__missing_series_metadataOracleTest : OracleTest() {
  private val conn = OracleDb.mainConnectionAt("20210617114814")
  private val context =
    object : Context {
      override fun getConfiguration(): Configuration? = null

      override fun getConnection(): Connection = conn
    }

  private fun rows(sql: String) = OracleDb.query(conn, sql)

  override fun cases() {
    func("migrate") {
      case("nothing missing") {
        V20210624165023__missing_series_metadata().migrate(context)
        rows("select count(*) from SERIES_METADATA")
      }
      case("creates metadata and aggregation") {
        OracleDb.exec(
          conn,
          "insert into LIBRARY(ID, NAME, ROOT) values ('L1', 'lib', 'file:/lib')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S1', '2020-01-01 00:00:00', 'Déjà vu', 'file:/lib/S1', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S2', '2020-01-01 00:00:00', 'with metadata', 'file:/lib/S2', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S3', '2020-01-01 00:00:00', 'Ünïcödé Ωmega', 'file:/lib/S3', 'L1')",
          "insert into SERIES_METADATA(SERIES_ID, STATUS, TITLE, TITLE_SORT) values ('S2','ENDED','kept','kept')",
          "insert into BOOK_METADATA_AGGREGATION(SERIES_ID) values ('S2')",
        )
        V20210624165023__missing_series_metadata().migrate(context)
        listOf(rows("select SERIES_ID, STATUS, TITLE, TITLE_SORT, READING_DIRECTION, AGE_RATING from SERIES_METADATA order by SERIES_ID"), rows("select SERIES_ID, RELEASE_DATE, SUMMARY from BOOK_METADATA_AGGREGATION order by SERIES_ID"))
      }
      case("second run is a no-op") {
        V20210624165023__missing_series_metadata().migrate(context)
        rows("select count(*) from SERIES_METADATA")
      }
    }
  }
}
