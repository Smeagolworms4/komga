package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.ReadListRequestBook
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class ReadListRequestDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.readListRequestDao

  private fun attempt(block: () -> Any?): Any? =
    try {
      block()
    } catch (e: Throwable) {
      "throws ${e::class.java.simpleName}"
    }

  private fun req(
    number: String,
    vararg series: String,
  ) = ReadListRequestBook(series.toSet(), number)

  override fun cases() {
    func("matchBookRequests") {
      case("empty database") { dao.matchBookRequests(listOf(req("1", "Batman"))) }
      case("single match") {
        NzDaoSeed.seed(db)
        dao.matchBookRequests(listOf(req("1", "Batman")))
      }
      case("series title ignoring ascii case and leading zeros") { dao.matchBookRequests(listOf(req("001", "BATMAN"), req("1", "Zorro"), req("01", "zorro"))) }
      case("non ascii case is not ignored") { dao.matchBookRequests(listOf(req("2", "élan vital"), req("2", "Élan vital"))) }
      case("several series aliases") { dao.matchBookRequests(listOf(req("3", "Nope", "batman", "Batman"))) }
      case("decimal number and deleted book") { dao.matchBookRequests(listOf(req("1.5", "ナルト"), req("2", "ナルト"))) }
      case("no match") { dao.matchBookRequests(listOf(req("99", "Batman"), req("1", "Unknown"), req("0", "Batman"))) }
      case("order of requests is kept") { dao.matchBookRequests(listOf(req("1", "Ångström"), req("2", "Batman"), req("1", "Æon Flux"))).map { it.matches.keys.map { s -> s.id } } }
      case("empty series set") { attempt { dao.matchBookRequests(listOf(req("1"))) } }
      case("empty requests") { attempt { dao.matchBookRequests(emptyList()) } }
    }
  }
}
