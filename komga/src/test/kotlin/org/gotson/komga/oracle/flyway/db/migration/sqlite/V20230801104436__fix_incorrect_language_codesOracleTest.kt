package org.gotson.komga.oracle.flyway.db.migration.sqlite

import db.migration.sqlite.V20230801104436__fix_incorrect_language_codes
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.sql.Connection

/** The migration runs on a database migrated up to the previous version (20230724114349), filled by each case */
class V20230801104436__fix_incorrect_language_codesOracleTest : OracleTest() {
  private val conn = OracleDb.mainConnectionAt("20230724114349")
  private val context =
    object : Context {
      override fun getConfiguration(): Configuration? = null

      override fun getConnection(): Connection = conn
    }

  private fun rows(sql: String) = OracleDb.query(conn, sql)

  override fun cases() {
    func("migrate") {
      case("no language") {
        V20230801104436__fix_incorrect_language_codes().migrate(context)
        rows("select count(*) from SERIES_METADATA")
      }
      case("normalizes codes") {
        OracleDb.exec(
          conn,
          "insert into LIBRARY(ID, NAME, ROOT) values ('L1', 'lib', 'file:/lib')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S1', '2020-01-01 00:00:00', 's1', 'file:/lib/S1', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S2', '2020-01-01 00:00:00', 's2', 'file:/lib/S2', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S3', '2020-01-01 00:00:00', 's3', 'file:/lib/S3', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S4', '2020-01-01 00:00:00', 's4', 'file:/lib/S4', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S5', '2020-01-01 00:00:00', 's5', 'file:/lib/S5', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S6', '2020-01-01 00:00:00', 's6', 'file:/lib/S6', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S7', '2020-01-01 00:00:00', 's7', 'file:/lib/S7', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S8', '2020-01-01 00:00:00', 's8', 'file:/lib/S8', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S9', '2020-01-01 00:00:00', 's9', 'file:/lib/S9', 'L1')",
          "insert into SERIES(ID, FILE_LAST_MODIFIED, NAME, URL, LIBRARY_ID) values ('S10', '2020-01-01 00:00:00', 's10', 'file:/lib/S10', 'L1')",
          "insert into SERIES_METADATA(SERIES_ID, STATUS, TITLE, TITLE_SORT, LANGUAGE) values ('S1','ONGOING','t','t','en'), ('S2','ONGOING','t','t','EN'), ('S3','ONGOING','t','t','fr_FR'), ('S4','ONGOING','t','t','zh-hant-tw'), ('S5','ONGOING','t','t',''), ('S6','ONGOING','t','t','  '), ('S7','ONGOING','t','t','english'), ('S8','ONGOING','t','t','ja-jp'), ('S9','ONGOING','t','t','en-GB-oed'), ('S10','ONGOING','t','t','i-klingon')",
        )
        V20230801104436__fix_incorrect_language_codes().migrate(context)
        rows("select SERIES_ID, LANGUAGE from SERIES_METADATA order by cast(substr(SERIES_ID, 2) as integer)")
      }
    }
    func("normalize") {
      val m = V20230801104436__fix_incorrect_language_codes()
      val normalize = V20230801104436__fix_incorrect_language_codes::class.java.getDeclaredMethod("normalize", String::class.java).apply { isAccessible = true }
      case("null") { normalize.invoke(m, null) }
      case("[en]") { normalize.invoke(m, "en") }
      case("[EN]") { normalize.invoke(m, "EN") }
      case("[fr_FR]") { normalize.invoke(m, "fr_FR") }
      case("[fr-FR]") { normalize.invoke(m, "fr-FR") }
      case("[zh-hant-tw]") { normalize.invoke(m, "zh-hant-tw") }
      case("[]") { normalize.invoke(m, "") }
      case("[   ]") { normalize.invoke(m, "   ") }
      case("[english]") { normalize.invoke(m, "english") }
      case("[x]") { normalize.invoke(m, "x") }
      case("[123]") { normalize.invoke(m, "123") }
      case("[und]") { normalize.invoke(m, "und") }
      case("[i-klingon]") { normalize.invoke(m, "i-klingon") }
      case("[iw]") { normalize.invoke(m, "iw") }
      case("[en-GB-oed]") { normalize.invoke(m, "en-GB-oed") }
      case("[zh-min-nan]") { normalize.invoke(m, "zh-min-nan") }
      case("[de-DE-1996]") { normalize.invoke(m, "de-DE-1996") }
      case("[ja_JP]") { normalize.invoke(m, "ja_JP") }
      case("[EN-us]") { normalize.invoke(m, "EN-us") }
      case("[sgn-BE-FR]") { normalize.invoke(m, "sgn-BE-FR") }
      case("[qaa]") { normalize.invoke(m, "qaa") }
      case("[a-b-c]") { normalize.invoke(m, "a-b-c") }
      case("[é]") { normalize.invoke(m, "é") }
    }
  }
}
