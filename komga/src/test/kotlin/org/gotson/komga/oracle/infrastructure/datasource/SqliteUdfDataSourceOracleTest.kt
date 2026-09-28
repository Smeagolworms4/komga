package org.gotson.komga.oracle.infrastructure.datasource

import org.gotson.komga.infrastructure.datasource.SqliteUdfDataSource
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.sql.Connection

class SqliteUdfDataSourceOracleTest : OracleTest() {
  private val connection: Connection by lazy {
    SqliteUdfDataSource()
      .apply { url = "jdbc:sqlite::memory:" }
      .connection
  }

  private fun q(sql: String) = OracleDb.query(connection, sql)

  private fun one(sql: String) = q(sql).single().single()

  /** whether the statement fails (the SQL error messages are not compared) */
  private fun fails(sql: String) = exceptionType { q(sql) } != null

  /** strings sorted with a collation, ties broken by insertion order */
  private fun sorted(
    collation: String,
    values: List<String>,
    desc: Boolean = false,
  ): List<Any?> {
    val rows = values.withIndex().joinToString(" union all ") { (i, v) -> "select ${sqlString(v)} as v, $i as i" }
    return q("select v from ($rows) order by v collate $collation ${if (desc) "desc" else "asc"}, i").map { it.single() }
  }

  private fun sqlString(s: String) = "'" + s.replace("'", "''") + "'"

  private fun regexp(
    text: String?,
    pattern: String?,
  ) = one("select ${text?.let { sqlString(it) } ?: "null"} regexp ${pattern?.let { sqlString(it) } ?: "null"}")

  private val words =
    listOf(
      "b",
      "a",
      "B",
      "A",
      "á",
      "Á",
      "à",
      "ä",
      "æ",
      "ae",
      "Æ",
      "z",
      "Z",
      "ž",
      "é",
      "e",
      "É",
      "E",
      "ê",
      "ë",
      "",
      " ",
      "1",
      "10",
      "2",
      "_",
      "-",
      "!",
      "a b",
      "ab",
      "ß",
      "ss",
      "SS",
      "ø",
      "o",
      "œ",
      "oe",
      "ı",
      "i",
      "I",
      "İ",
      "漫画",
      "まんが",
      "マンガ",
      "한국어",
      "Ω",
      "ω",
      "ǅ",
      "ǆ",
      "Ǆ",
      "ﬁ",
      "fi",
      "Ⅻ",
      "xii",
      "①",
      "½",
    )

  override fun cases() {
    func("getConnection@22") {
      case("udf and collations available") {
        listOf(
          one("select UDF_STRIP_ACCENTS('Éé')"),
          one("select 'ABC' regexp 'b'"),
          one("select 'a' = 'A' collate COLLATION_UNICODE_1"),
          one("select 'a' = 'A' collate COLLATION_UNICODE_3"),
        )
      }
      case("new connection each time") {
        val ds = SqliteUdfDataSource().apply { url = "jdbc:sqlite::memory:" }
        ds.connection.use { c1 ->
          ds.connection.use { c2 ->
            OracleDb.exec(c1, "create table T (A varchar)")
            listOf(OracleDb.query(c1, "select count(*) from sqlite_master"), OracleDb.query(c2, "select count(*) from sqlite_master"))
          }
        }
      }
      case("foreign keys not enforced by default") { q("pragma foreign_keys") }
    }

    func("getConnection@24") {
      case("udf available") {
        SqliteUdfDataSource()
          .apply { url = "jdbc:sqlite::memory:" }
          .getConnection(null, null)
          .use { OracleDb.query(it, "select UDF_STRIP_ACCENTS('Ç'), 'x' regexp 'X', 'é' < 'f' collate COLLATION_UNICODE_3") }
      }
    }

    func("addAllUdf") {
      case("functions") { q("select name, narg from pragma_function_list where name in ('regexp', 'udf_strip_accents') order by name") }
      case("collations") { q("select name from pragma_collation_list where name like 'COLLATION_UNICODE_%' order by name") }
    }

    func("createUdfRegexp") {
      case("matches anywhere") { regexp("xxABCxx", "abc") }
      case("no match") { regexp("xyz", "abc") }
      case("anchored") { listOf(regexp("Alpha", "^a"), regexp("beta", "^a"), regexp("Alpha", "A$")) }
      case("null pattern matches") { regexp("abc", null) }
      case("null text") { listOf(regexp(null, "^$"), regexp(null, "a")) }
      case("both null") { regexp(null, null) }
      case("empty pattern") { regexp("", "") }
      case("character classes") { listOf(regexp("Z", "[a-z]"), regexp("5", "\\d"), regexp("a", "\\D"), regexp(" ", "\\s"), regexp("_", "\\w")) }
      case("letter group") { listOf(regexp("Élan", "^[a-e]"), regexp("élan", "^[^a-z]"), regexp("123", "^[^a-z]"), regexp("#1", "^[0-9#]")) }
      case("non-ascii case") { listOf(regexp("élan", "^É"), regexp("ÉLAN", "élan"), regexp("straße", "STRASSE"), regexp("Ω", "ω")) }
      case("dot and newline") { listOf(regexp("a\nb", "a.b"), regexp("a\nb", "^b")) }
      case("alternation and groups") { listOf(regexp("the cat", "(dog|cat)$"), regexp("abab", "^(ab){2}$"), regexp("abab", "^(?:ab)+$")) }
      case("quantifiers") { listOf(regexp("aaa", "^a{2,3}$"), regexp("aaaa", "^a{2,3}$"), regexp("ab", "^a*?b")) }
      case("escapes") { listOf(regexp("a.b", "a\\.b"), regexp("axb", "a\\.b"), regexp("1+1", "1\\+1"), regexp("a\\b", "\\\\")) }
      case("unicode letters") { listOf(regexp("é", "^\\w$"), regexp("漫", "."), regexp("😀", "^.$")) }
      case("lookaround") { listOf(regexp("foobar", "foo(?=bar)"), regexp("foobaz", "foo(?!bar)"), regexp("xbar", "(?<=x)bar")) }
      case("backreference") { listOf(regexp("abcabc", "(abc)\\1"), regexp("abcabd", "^(abc)\\1$")) }
      case("word boundary") { listOf(regexp("a cat", "\\bcat\\b"), regexp("concat", "\\bcat\\b")) }
      case("numeric values") { listOf(one("select 12 regexp '^1'"), one("select 1.5 regexp '\\.5$'"), one("select 'x' regexp 1")) }
      case("invalid pattern") { fails("select 'a' regexp '('") }
      case("in where clause") { q("select v from (select 'Alpha' v union all select 'beta' union all select 'Gamma') where v regexp '^[a-c]' order by v") }
    }

    func("xFunc@42") {
      case("returns 1 or 0") { q("select 'a' regexp 'a', 'a' regexp 'b', typeof('a' regexp 'a')") }
      case("negated") { one("select 'a' not regexp 'b'") }
    }

    func("createUdfStripAccents") {
      case("accents") { one("select UDF_STRIP_ACCENTS('àáâãäåçèéêëìíîïñòóôõöùúûüýÿ ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ')") }
      case("ligatures and special letters") { one("select UDF_STRIP_ACCENTS('æ Æ œ Œ ß ø Ø đ Đ ł Ł ı ﬁ ǅ')") }
      case("combining characters") { one("select UDF_STRIP_ACCENTS('e' || char(769) || 'a' || char(776))") }
      case("other scripts") { one("select UDF_STRIP_ACCENTS('Ελληνικά Ά ё й 漫画 まんが ガ Tiếng Việt')") }
      case("empty") { one("select UDF_STRIP_ACCENTS('')") }
      case("number") { one("select UDF_STRIP_ACCENTS(12)") }
      case("null fails") { fails("select UDF_STRIP_ACCENTS(null)") }
      case("in where clause") { q("select v from (select 'Élan' v union all select 'elan' union all select 'ELAN') where UDF_STRIP_ACCENTS(v) = 'Elan'") }
    }

    func("xFunc@58") {
      case("type") { q("select typeof(UDF_STRIP_ACCENTS('a')), length(UDF_STRIP_ACCENTS('é'))") }
    }

    func("createUnicodeCollation") {
      case("unicode 3 order") { sorted(SqliteUdfDataSource.COLLATION_UNICODE_3, words) }
      case("unicode 1 order") { sorted(SqliteUdfDataSource.COLLATION_UNICODE_1, words) }
      case("unicode 3 order desc") { sorted(SqliteUdfDataSource.COLLATION_UNICODE_3, words, true) }
      case("binary order") { sorted("BINARY", words) }
      case("nocase order") { sorted("NOCASE", words) }
    }

    func("xCompare@77") {
      val pairs =
        listOf("a" to "A", "a" to "á", "e" to "É", "ae" to "æ", "ss" to "ß", "i" to "ı", "a" to "a ", "" to " ", "1" to "①", "fi" to "ﬁ", "ǅ" to "Ǆ", "マンガ" to "まんが", "a" to "b")
      case("unicode 1 equality") { pairs.map { (x, y) -> one("select ${sqlString(x)} = ${sqlString(y)} collate COLLATION_UNICODE_1") } }
      case("unicode 3 equality") { pairs.map { (x, y) -> one("select ${sqlString(x)} = ${sqlString(y)} collate COLLATION_UNICODE_3") } }
      case("unicode 3 less than") { pairs.map { (x, y) -> one("select ${sqlString(x)} < ${sqlString(y)} collate COLLATION_UNICODE_3") } }
      case("unicode 1 distinct") { q("select count(distinct v collate COLLATION_UNICODE_1) from (select 'a' v union all select 'A' union all select 'à' union all select 'b')") }
      case("unicode 1 group by") {
        q("select min(v), count(*) from (select 'a' v union all select 'A' union all select 'à' union all select 'b') group by v collate COLLATION_UNICODE_1 order by 1")
      }
    }
  }
}
