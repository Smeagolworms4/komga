package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.user

class UserDtoOracleTest : OracleTest() {
  private val restricted =
    user(
      "U2",
      roles = setOf(UserRoles.PAGE_STREAMING, UserRoles.FILE_DOWNLOAD),
      sharedLibrariesIds = setOf("L2", "L1"),
      sharedAllLibraries = false,
      restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.EXCLUDE), setOf("Kids", "b"), setOf("Adult")),
    )

  override fun cases() {
    func("toDto@26") {
      case("allow only") { AgeRestriction(16, AllowExclude.ALLOW_ONLY).toDto() }
      case("exclude") { AgeRestriction(0, AllowExclude.EXCLUDE).toDto() }
    }
    func("toDto@28") {
      case("no roles") { user("U1").toDto() }
      case("admin") { user("U1", roles = UserRoles.entries.toSet()).toDto() }
      case("restricted") { restricted.toDto() }
      case("json restricted") { json(restricted.toDto()) }
      case("json no age restriction") { json(user("U1").toDto()) }
    }
    func("toDto@40") {
      case("principal") { principal(restricted).toDto() }
    }
  }
}
