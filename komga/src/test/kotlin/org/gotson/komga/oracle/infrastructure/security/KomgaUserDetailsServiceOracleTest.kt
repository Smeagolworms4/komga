package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.KomgaUserDetailsService
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class KomgaUserDetailsServiceOracleTest : OracleTest() {
  private val db = OracleDb()
  private val service = KomgaUserDetailsService(db.komgaUserDao)

  private fun load(name: String) = (service.loadUserByUsername(name) as KomgaPrincipal).let { listOf(it.user.id, it.username, it.password, it.authorities.map { a -> a.authority }.sorted()) }

  override fun cases() {
    func("loadUserByUsername") {
      case("populate") {
        db.komgaUserDao.insert(KomgaUser("User@Example.org", "pw", id = "U1", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)))
        db.komgaUserDao.insert(KomgaUser("émile@exemple.fr", "pw2", id = "U2", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0)))
      }
      case("exact email") { load("User@Example.org") }
      case("lower case") { load("user@example.org") }
      case("upper case") { load("USER@EXAMPLE.ORG") }
      case("unicode lower") { load("émile@exemple.fr") }
      case("unicode upper") { load("ÉMILE@EXEMPLE.FR") }
      case("unknown") { load("nobody@example.org") }
      case("empty") { load("") }
      case("with spaces") { load(" user@example.org ") }
    }
  }
}
