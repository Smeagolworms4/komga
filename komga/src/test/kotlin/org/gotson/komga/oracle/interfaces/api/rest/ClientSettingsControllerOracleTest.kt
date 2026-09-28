package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.ClientSettingsController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user

class ClientSettingsControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val controller = ClientSettingsController(db.clientSettingsDtoDao)
  private val u1 = user("U1")
  private val u2 = user("U2")

  override fun cases() {
    func("getGlobalSettings") {
      case("empty, anonymous") { controller.getGlobalSettings(null) }
      case("seeded, anonymous") {
        db.komgaUserDao.insert(u1)
        db.komgaUserDao.insert(u2)
        db.clientSettingsDtoDao.saveGlobal("app.b", "vb", false)
        db.clientSettingsDtoDao.saveGlobal("app.a", "va", true)
        db.clientSettingsDtoDao.saveGlobal("app.c", """{"json":1}""", true)
        db.clientSettingsDtoDao.saveForUser("U1", "user.x", "x1")
        db.clientSettingsDtoDao.saveForUser("U1", "user.y", "y1")
        db.clientSettingsDtoDao.saveForUser("U2", "user.x", "x2")
        controller.getGlobalSettings(null)
      }
      case("authenticated") { controller.getGlobalSettings(principal(u1)) }
    }
    func("getUserSettings") {
      case("U1") { controller.getUserSettings(principal(u1)) }
      case("U2") { controller.getUserSettings(principal(u2)) }
      case("unknown user") { controller.getUserSettings(principal(user("U3"))) }
    }
    func("deleteGlobalSettings") {
      case("unknown key") {
        controller.deleteGlobalSettings(setOf("nope"))
        controller.getGlobalSettings(principal(u1))
      }
      case("two keys") {
        controller.deleteGlobalSettings(setOf("app.a", "app.c"))
        listOf(controller.getGlobalSettings(principal(u1)), controller.getUserSettings(principal(u1)))
      }
      case("empty set") {
        controller.deleteGlobalSettings(emptySet())
        controller.getGlobalSettings(principal(u1))
      }
    }
  }
}
