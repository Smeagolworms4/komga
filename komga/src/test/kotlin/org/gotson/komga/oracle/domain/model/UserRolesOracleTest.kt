package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.oracle.OracleTest

class UserRolesOracleTest : OracleTest() {
  override fun cases() {
    func("entries") {
      case("names and ordinals") { UserRoles.entries.map { listOf(it.name, it.ordinal) } }
    }
    func("valuesOf") {
      case("empty") { UserRoles.valuesOf(emptyList()) }
      case("all") { UserRoles.valuesOf(UserRoles.entries.map { it.name }) }
      case("reversed order") { UserRoles.valuesOf(listOf("KOREADER_SYNC", "ADMIN", "FILE_DOWNLOAD")) }
      case("invalid ignored") { UserRoles.valuesOf(listOf("admin", "ADMIN ", " ADMIN", "USER", "", "PAGE_STREAMING")) }
      case("duplicates") { UserRoles.valuesOf(listOf("ADMIN", "KOBO_SYNC", "ADMIN")) }
      case("set source") { UserRoles.valuesOf(setOf("KOBO_SYNC", "ROLE_ADMIN")) }
    }
  }
}
