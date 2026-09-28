package org.gotson.komga.oracle.interfaces.scheduler

import org.gotson.komga.domain.model.AuthenticationActivity
import org.gotson.komga.interfaces.scheduler.AuthenticationActivityCleanupController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime
import java.time.ZoneId

class AuthenticationActivityCleanupControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val controller = AuthenticationActivityCleanupController(db.authenticationActivityDao)

  private fun emails() = db.rawQuery("SELECT EMAIL FROM AUTHENTICATION_ACTIVITY ORDER BY EMAIL").map { it.first() }

  override fun cases() {
    func("cleanup") {
      case("empty") {
        controller.cleanup()
        emails()
      }
      case("old activities removed") {
        val now = LocalDateTime.now(ZoneId.of("Z"))
        listOf(
          "a-2000" to LocalDateTime.of(2000, 1, 1, 0, 0),
          "b-40-days" to now.minusDays(40),
          "c-20-days" to now.minusDays(20),
          "d-now" to now,
          "e-future" to now.plusDays(3),
        ).forEach { (email, date) -> db.authenticationActivityDao.insert(AuthenticationActivity(email = email, dateTime = date, success = true)) }
        controller.cleanup()
        emails()
      }
      case("idempotent") {
        controller.cleanup()
        emails()
      }
    }
  }
}
