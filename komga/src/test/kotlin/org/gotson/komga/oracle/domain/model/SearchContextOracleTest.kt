package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class SearchContextOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  override fun cases() {
    func("empty") {
      case("value") { SearchContext.empty() }
      case("distinct instances") { SearchContext.empty() === SearchContext.empty() }
    }
    func("ofAnonymousUser") {
      case("value") { SearchContext.ofAnonymousUser() }
      case("not restricted") { SearchContext.ofAnonymousUser().restrictions.isRestricted }
    }
    func("<init>") {
      case("null user") { SearchContext(null) }
      case("limited user") {
        SearchContext(KomgaUser("a@b.c", "p", sharedLibrariesIds = setOf("L2", "L1"), sharedAllLibraries = false, id = "U", createdDate = date))
      }
      case("admin user") {
        SearchContext(KomgaUser("a@b.c", "p", roles = setOf(UserRoles.ADMIN), sharedAllLibraries = false, id = "A", createdDate = date))
      }
      case("restricted user") {
        SearchContext(
          KomgaUser(
            "a@b.c",
            "p",
            restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY), setOf("Kids")),
            id = "R",
            createdDate = date,
          ),
        )
      }
    }
  }
}
