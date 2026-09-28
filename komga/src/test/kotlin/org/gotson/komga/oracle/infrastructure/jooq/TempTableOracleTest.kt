package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.infrastructure.jooq.TempTable
import org.gotson.komga.infrastructure.jooq.TempTable.Companion.withTempTable
import org.gotson.komga.jooq.main.Tables
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render

class TempTableOracleTest : OracleTest() {
  private val db = OracleDb()
  private val s = Tables.SERIES

  private fun tempTables() = db.rawQuery("select count(*) from sqlite_temp_master where type = 'table'")

  private fun strings(t: TempTable) = t.selectTempStrings().fetch().map { it.value1() }

  /** the generated name is replaced, it holds a TSID */
  private fun renderTemp(
    t: TempTable,
    q: org.jooq.Query,
  ) = render(q).map { if (it is String) it.replace(t.name, "<temp>") else it }

  override fun cases() {
    func("create") {
      case("sample rows") { JooqSamples.insert(db) }
      case("creates a temporary table") {
        val t = TempTable(db.dsl)
        t.create()
        listOf(tempTables(), strings(t)).also { t.close() }
      }
      case("twice") {
        val t = TempTable(db.dsl)
        t.create()
        exceptionType { t.create() }.also { t.close() }
      }
      case("not null error message") {
        val t = TempTable(db.dsl)
        t.create()
        try {
          db.dsl.execute("insert into ${t.name} values (null)")
          null
        } catch (e: Exception) {
          e.message?.replace(t.name, "<temp>")
        }.also { t.close() }
      }
      case("unique error message") {
        try {
          db.dsl.execute("insert into LIBRARY(ID, NAME, ROOT) values ('L1', 'x', 'y')")
          null
        } catch (e: Exception) {
          e.message
        }
      }
      case("column accepts no null") {
        val t = TempTable(db.dsl)
        t.create()
        exceptionType { db.dsl.execute("insert into ${t.name} values (null)") }.also { t.close() }
      }
    }

    func("insertTempStrings") {
      case("creates the table when needed") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(10, listOf("a"))
        listOf(tempTables(), strings(t)).also { t.close() }
      }
      case("several chunks") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(2, listOf("e", "d", "c", "b", "a"))
        strings(t).also { t.close() }
      }
      case("chunk size 1") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(1, setOf("x", "y"))
        strings(t).also { t.close() }
      }
      case("duplicates, empty and unicode strings") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(3, listOf("a", "a", "", "Ça été", "漫画", "it's", "\"q\""))
        strings(t).also { t.close() }
      }
      case("empty collection creates the table") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(5, emptyList())
        listOf(tempTables(), strings(t)).also { t.close() }
      }
      case("called twice appends") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(5, listOf("1", "2"))
        t.insertTempStrings(5, listOf("3"))
        strings(t).also { t.close() }
      }
      case("after create") {
        val t = TempTable(db.dsl)
        t.create()
        t.insertTempStrings(5, listOf("z"))
        strings(t).also { t.close() }
      }
      case("chunk size 0") {
        val t = TempTable(db.dsl)
        listOf(exceptionType { t.insertTempStrings(0, listOf("a")) }, tempTables()).also { t.close() }
      }
      case("chunk size 0 with empty collection") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(0, emptyList())
        strings(t).also { t.close() }
      }
      case("many values") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(7, (1..100).map { "v$it" })
        strings(t).let { listOf(it.size, it.first(), it.last()) }.also { t.close() }
      }
    }

    func("selectTempStrings") {
      case("render") {
        val t = TempTable(db.dsl)
        renderTemp(t, t.selectTempStrings())
      }
      case("as sub-select") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(10, listOf("S3", "S1", "nope"))
        db.dsl
          .select(s.ID)
          .from(s)
          .where(s.ID.`in`(t.selectTempStrings()))
          .orderBy(s.ID)
          .fetch(s.ID)
          .also { t.close() }
      }
      case("render as sub-select") {
        val t = TempTable(db.dsl)
        renderTemp(
          t,
          db.dsl
            .select(s.ID)
            .from(s)
            .where(s.ID.notIn(t.selectTempStrings())),
        )
      }
      case("table not created") {
        val t = TempTable(db.dsl)
        exceptionType { t.selectTempStrings().fetch() }
      }
    }

    func("close") {
      case("drops the table") {
        val t = TempTable(db.dsl)
        t.insertTempStrings(10, listOf("a"))
        val before = tempTables()
        t.close()
        listOf(before, tempTables())
      }
      case("not created") {
        val t = TempTable(db.dsl)
        t.close()
        tempTables()
      }
      case("twice") {
        val t = TempTable(db.dsl)
        t.create()
        t.close()
        t.close()
        tempTables()
      }
      case("use") {
        TempTable(db.dsl).use {
          it.insertTempStrings(10, listOf("u"))
          strings(it)
        } to tempTables()
      }
    }

    func("generateName") {
      case("format") { Regex("^temp_[0-9A-HJKMNP-TV-Z]{13}$").matches(TempTable(db.dsl).name) }
      case("unique") { (1..20).map { TempTable(db.dsl).name }.toSet().size }
    }

    func("withTempTable") {
      case("inserts the values") {
        db.dsl.withTempTable(2, listOf("S2", "S6", "S2")).use { t ->
          db.dsl
            .select(s.ID)
            .from(s)
            .where(s.ID.`in`(t.selectTempStrings()))
            .orderBy(s.ID)
            .fetch(s.ID) to strings(t)
        }
      }
      case("empty collection") {
        db.dsl.withTempTable(2, emptySet()).use { t ->
          db.dsl
            .select(s.ID)
            .from(s)
            .where(s.ID.notIn(t.selectTempStrings()))
            .orderBy(s.ID)
            .fetch(s.ID)
        }
      }
      case("dropped after use") { tempTables() }
      case("generated name") {
        db.dsl.withTempTable(1, listOf("a")).use { Regex("^temp_[0-9A-HJKMNP-TV-Z]{13}$").matches(it.name) }
      }
    }
  }
}
