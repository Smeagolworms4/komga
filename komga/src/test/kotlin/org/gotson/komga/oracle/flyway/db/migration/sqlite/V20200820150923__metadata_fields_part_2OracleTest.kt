package org.gotson.komga.oracle.flyway.db.migration.sqlite

import db.migration.sqlite.V20200820150923__metadata_fields_part_2
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.sql.Connection

/** The migration runs on a database migrated up to the previous version (20200820141405), filled by each case */
class V20200820150923__metadata_fields_part_2OracleTest : OracleTest() {
  private val conn = OracleDb.mainConnectionAt("20200820141405")
  private val context =
    object : Context {
      override fun getConfiguration(): Configuration? = null

      override fun getConnection(): Connection = conn
    }

  private fun rows(sql: String) = OracleDb.query(conn, sql)

  override fun cases() {
    func("migrate") {
      case("no book metadata") {
        rows("select count(*) from SERIES_METADATA")
      }
      case("aggregates per series") {
        OracleDb.exec(
          conn,
          "insert into LIBRARY(ID, NAME, ROOT) values ('L1', 'lib', 'file:/lib')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S1', '2020-01-01 00:00:00', 's1', 'file:/lib/S1', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S2', '2020-01-01 00:00:00', 's2', 'file:/lib/S2', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S3', '2020-01-01 00:00:00', 's3', 'file:/lib/S3', 'L1')",
          "insert into SERIES_METADATA(SERIES_ID, STATUS, TITLE, TITLE_SORT) values ('S1','ONGOING','s1','s1'), ('S2','ONGOING','s2','s2'), ('S3','ONGOING','s3','s3')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B1', '2020-01-01 00:00:00', 'book B1', 'file:/lib/S1/B1.cbz', 'S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B2', '2020-01-01 00:00:00', 'book B2', 'file:/lib/S1/B2.cbz', 'S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B3', '2020-01-01 00:00:00', 'book B3', 'file:/lib/S1/B3.cbz', 'S1', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B4', '2020-01-01 00:00:00', 'book B4', 'file:/lib/S2/B4.cbz', 'S2', 'L1')",
          "insert into BOOK(ID, FILE_LAST_MODIFIED, NAME, URL, SERIES_ID, LIBRARY_ID) values ('B5', '2020-01-01 00:00:00', 'book B5', 'file:/lib/S3/B5.cbz', 'S3', 'L1')",
          "insert into BOOK_METADATA(BOOK_ID, TITLE, NUMBER, NUMBER_SORT, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK) values ('B1','t1','1',1.0,12,0,'Pub A',0,'LEFT_TO_RIGHT',1)",
          "insert into BOOK_METADATA(BOOK_ID, TITLE, NUMBER, NUMBER_SORT, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK) values ('B2','t2','2',2.5,16,1,'Pub B',1,'RIGHT_TO_LEFT',0)",
          "insert into BOOK_METADATA(BOOK_ID, TITLE, NUMBER, NUMBER_SORT, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK) values ('B3','t3','3',3.0,null,0,'',0,'RIGHT_TO_LEFT',0)",
          "insert into BOOK_METADATA(BOOK_ID, TITLE, NUMBER, NUMBER_SORT, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK) values ('B4','t4','1',1.0,null,0,'',0,null,0)",
          "insert into BOOK_METADATA(BOOK_ID, TITLE, NUMBER, NUMBER_SORT, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK) values ('B5','t5','x',-1.0,0,0,'Ünï',0,'WEBTOON',0)",
        )
        V20200820150923__metadata_fields_part_2().migrate(context)
        rows("select SERIES_ID, AGE_RATING, AGE_RATING_LOCK, PUBLISHER, PUBLISHER_LOCK, READING_DIRECTION, READING_DIRECTION_LOCK from SERIES_METADATA order by SERIES_ID")
      }
    }
  }
}
