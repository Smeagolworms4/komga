package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class ClientSettingsDtoDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.clientSettingsDtoDao

  override fun cases() {
    func("findAllGlobal") {
      case("empty") { dao.findAllGlobal() }
    }

    func("saveGlobal") {
      case("insert") {
        dao.saveGlobal("theme", "dark", true)
        dao.saveGlobal("secret", "{\"a\": 1, \"ü\": [\"x\"]}", false)
        dao.findAllGlobal().toSortedMap()
      }
      case("update keeps allow unauthorized") {
        dao.saveGlobal("theme", "light", false)
        dao.findAllGlobal()["theme"]
      }
      case("empty value") {
        dao.saveGlobal("empty", "", false)
        dao.findAllGlobal()["empty"]
      }
    }

    func("findAllGlobal") {
      case("only unauthorized") { dao.findAllGlobal(true) }
      case("all") { dao.findAllGlobal(false).keys.sorted() }
    }

    func("saveForUser") {
      case("insert") {
        NzDaoSeed.seed(db)
        dao.saveForUser("U1", "theme", "dark")
        dao.saveForUser("U1", "lang", "fr")
        dao.saveForUser("U2", "theme", "sepia")
        dao.findAllUser("U1").toSortedMap()
      }
      case("update") {
        dao.saveForUser("U1", "theme", "light")
        dao.findAllUser("U1")["theme"]
      }
      case("unknown user") { exceptionType { dao.saveForUser("NOPE", "k", "v") } }
    }

    func("findAllUser") {
      case("other user") { dao.findAllUser("U2") }
      case("no setting") { dao.findAllUser("U3") }
    }

    func("deleteGlobalByKeys") {
      case("some keys") {
        dao.deleteGlobalByKeys(listOf("secret", "NOPE"))
        dao.findAllGlobal().keys.sorted()
      }
      case("empty list") {
        dao.deleteGlobalByKeys(emptyList())
        dao.findAllGlobal().size
      }
    }

    func("deleteByUserIdAndKeys") {
      case("only for user") {
        dao.deleteByUserIdAndKeys("U1", listOf("theme"))
        listOf(dao.findAllUser("U1"), dao.findAllUser("U2"))
      }
      case("empty list") {
        dao.deleteByUserIdAndKeys("U2", emptyList())
        dao.findAllUser("U2").size
      }
    }

    func("deleteByUserId") {
      case("existing") {
        dao.deleteByUserId("U2")
        listOf(dao.findAllUser("U2"), dao.findAllUser("U1").size)
      }
    }

    func("deleteAll") {
      case("all") {
        dao.saveForUser("U3", "a", "b")
        dao.deleteAll()
        listOf(dao.findAllGlobal(), dao.findAllUser("U1"), dao.findAllUser("U3"))
      }
    }
  }
}
