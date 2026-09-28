package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class ServerSettingsDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.serverSettingsDao

  private fun all() = db.rawQuery("select KEY, VALUE from SERVER_SETTINGS where KEY <> 'REMEMBER_ME_KEY' order by KEY")

  override fun cases() {
    func("getSettingByKey") {
      case("missing key as string") { dao.getSettingByKey("NOPE", String::class.java) }
      case("missing key as int") { dao.getSettingByKey("NOPE", Int::class.java) }
      case("missing key as boolean") { dao.getSettingByKey("NOPE", Boolean::class.java) }
    }

    func("saveSetting@26") {
      case("insert") {
        dao.saveSetting("K1", "valeur ünïcode 漫画")
        dao.getSettingByKey("K1", String::class.java)
      }
      case("update existing key") {
        dao.saveSetting("K1", "other")
        all()
      }
      case("empty value") {
        dao.saveSetting("EMPTY", "")
        dao.getSettingByKey("EMPTY", String::class.java)
      }
      case("keys are case sensitive") {
        dao.saveSetting("k1", "lower")
        listOf(dao.getSettingByKey("K1", String::class.java), dao.getSettingByKey("k1", String::class.java))
      }
      case("numeric string") {
        dao.saveSetting("NUM", "0042")
        listOf(dao.getSettingByKey("NUM", String::class.java), dao.getSettingByKey("NUM", Int::class.java))
      }
    }

    func("saveSetting@38") {
      case("true") {
        dao.saveSetting("BOOL", true)
        listOf(dao.getSettingByKey("BOOL", String::class.java), dao.getSettingByKey("BOOL", Boolean::class.java))
      }
      case("false overwrites") {
        dao.saveSetting("BOOL", false)
        listOf(dao.getSettingByKey("BOOL", String::class.java), dao.getSettingByKey("BOOL", Boolean::class.java))
      }
    }

    func("saveSetting@45") {
      case("positive") {
        dao.saveSetting("INT", 365)
        listOf(dao.getSettingByKey("INT", String::class.java), dao.getSettingByKey("INT", Int::class.java))
      }
      case("negative") {
        dao.saveSetting("INT", -12)
        dao.getSettingByKey("INT", Int::class.java)
      }
      case("max int") {
        dao.saveSetting("INT", Int.MAX_VALUE)
        dao.getSettingByKey("INT", Int::class.java)
      }
    }

    func("getSettingByKey") {
      case("string value") { dao.getSettingByKey("K1", String::class.java) }
      case("int read as string") { dao.getSettingByKey("INT", String::class.java) }
      case("boolean from 1") {
        dao.saveSetting("B1", "1")
        dao.getSettingByKey("B1", Boolean::class.java)
      }
      case("boolean from 0") {
        dao.saveSetting("B0", "0")
        dao.getSettingByKey("B0", Boolean::class.java)
      }
      case("boolean from TRUE") {
        dao.saveSetting("BT", "TRUE")
        dao.getSettingByKey("BT", Boolean::class.java)
      }
      case("boolean from int value") { dao.getSettingByKey("INT", Boolean::class.java) }
      case("int from boolean text") { exceptionType { dao.getSettingByKey("BOOL", Int::class.java) } }
      case("int from boolean text value") { dao.getSettingByKey("BOOL", Int::class.java) }
      case("int from decimal text") {
        dao.saveSetting("DEC", "1.75")
        dao.getSettingByKey("DEC", Int::class.java)
      }
      case("int from blank text") {
        dao.saveSetting("BLANK", " ")
        dao.getSettingByKey("BLANK", Int::class.java)
      }
    }

    func("deleteSetting") {
      case("existing") {
        dao.deleteSetting("K1")
        listOf(dao.getSettingByKey("K1", String::class.java), dao.getSettingByKey("k1", String::class.java))
      }
      case("missing") {
        dao.deleteSetting("NOPE")
        all().size
      }
    }

    func("deleteAll") {
      case("all") {
        dao.deleteAll()
        all()
      }
      case("already empty") {
        dao.deleteAll()
        all()
      }
    }
  }
}
