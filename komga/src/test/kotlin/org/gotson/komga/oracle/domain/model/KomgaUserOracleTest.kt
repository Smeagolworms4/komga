package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class KomgaUserOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 5, 17, 10, 11, 12, 13000000)

  private fun user(
    roles: Set<UserRoles> = setOf(UserRoles.FILE_DOWNLOAD, UserRoles.PAGE_STREAMING),
    shared: Set<String> = emptySet(),
    all: Boolean = true,
    restrictions: ContentRestrictions = ContentRestrictions(),
  ) = KomgaUser("user@example.org", "secret", roles, shared, all, restrictions, "U1", date)

  private val admin = user(roles = setOf(UserRoles.ADMIN), all = false)
  private val limited = user(shared = setOf("L1", "L2"), all = false)
  private val limitedNone = user(all = false)
  private val unlimited = user(shared = setOf("L1"))

  private fun ageRestricted(
    age: Int,
    type: AllowExclude,
    allow: Set<String> = emptySet(),
    exclude: Set<String> = emptySet(),
  ) = user(restrictions = ContentRestrictions(AgeRestriction(age, type), allow, exclude))

  override fun cases() {
    func("getAuthorizedLibraryIds") {
      case("limited, null") { limited.getAuthorizedLibraryIds(null) }
      case("limited, filter") { limited.getAuthorizedLibraryIds(listOf("L2", "L3", "L1")) }
      case("limited, empty filter") { limited.getAuthorizedLibraryIds(emptyList()) }
      case("limited, duplicates") { limited.getAuthorizedLibraryIds(listOf("L1", "L1", "L2")) }
      case("limited none, null") { limitedNone.getAuthorizedLibraryIds(null) }
      case("limited none, filter") { limitedNone.getAuthorizedLibraryIds(listOf("L1")) }
      case("unlimited, null") { unlimited.getAuthorizedLibraryIds(null) }
      case("unlimited, filter") { unlimited.getAuthorizedLibraryIds(listOf("L9", "L1", "L9")) }
      case("unlimited, set filter") { unlimited.getAuthorizedLibraryIds(setOf("B", "A")) }
      case("admin, null") { admin.getAuthorizedLibraryIds(null) }
      case("admin, filter") { admin.getAuthorizedLibraryIds(listOf("X")) }
    }

    func("canAccessAllLibraries") {
      case("default") { user().canAccessAllLibraries() }
      case("limited") { limited.canAccessAllLibraries() }
      case("admin not shared all") { admin.canAccessAllLibraries() }
      case("admin among roles") { user(roles = setOf(UserRoles.PAGE_STREAMING, UserRoles.ADMIN), all = false).canAccessAllLibraries() }
      case("no roles") { user(roles = emptySet(), all = false).canAccessAllLibraries() }
    }

    func("canAccessLibrary@50") {
      case("limited, shared") { limited.canAccessLibrary("L1") }
      case("limited, not shared") { limited.canAccessLibrary("L3") }
      case("limited, case") { limited.canAccessLibrary("l1") }
      case("limited, empty") { limited.canAccessLibrary("") }
      case("unlimited") { unlimited.canAccessLibrary("anything") }
      case("admin") { admin.canAccessLibrary("L3") }
      case("limited none") { limitedNone.canAccessLibrary("L1") }
    }

    func("canAccessLibrary@52") {
      fun lib(id: String) = Library("n", URL("file:/x"), id = id, createdDate = date)
      case("limited, shared") { limited.canAccessLibrary(lib("L2")) }
      case("limited, not shared") { limited.canAccessLibrary(lib("L3")) }
      case("unlimited") { unlimited.canAccessLibrary(lib("L3")) }
      case("admin") { admin.canAccessLibrary(lib("L3")) }
    }

    func("isContentAllowed") {
      val none = user()
      case("no restriction, defaults") { none.isContentAllowed() }
      case("no restriction, age and labels") { none.isContentAllowed(18, setOf("x")) }

      val allow12 = ageRestricted(12, AllowExclude.ALLOW_ONLY)
      case("allow only 12, null age") { allow12.isContentAllowed() }
      case("allow only 12, 11") { allow12.isContentAllowed(11) }
      case("allow only 12, 12") { allow12.isContentAllowed(12) }
      case("allow only 12, 13") { allow12.isContentAllowed(13) }
      case("allow only 12, negative") { allow12.isContentAllowed(-1) }
      case("allow only 0, 0") { ageRestricted(0, AllowExclude.ALLOW_ONLY).isContentAllowed(0) }

      val exclude16 = ageRestricted(16, AllowExclude.EXCLUDE)
      case("exclude 16, null age") { exclude16.isContentAllowed() }
      case("exclude 16, 15") { exclude16.isContentAllowed(15) }
      case("exclude 16, 16") { exclude16.isContentAllowed(16) }
      case("exclude 16, 99") { exclude16.isContentAllowed(99) }
      case("exclude 16, min int") { exclude16.isContentAllowed(Int.MIN_VALUE) }

      val allowKids = user(restrictions = ContentRestrictions(labelsAllow = setOf("kids", "Family")))
      case("allow labels, none") { allowKids.isContentAllowed() }
      case("allow labels, match") { allowKids.isContentAllowed(sharingLabels = setOf("kids")) }
      case("allow labels, match case and spaces") { allowKids.isContentAllowed(sharingLabels = setOf(" FAMILY ")) }
      case("allow labels, no match") { allowKids.isContentAllowed(sharingLabels = setOf("adult")) }
      case("allow labels, blank labels") { allowKids.isContentAllowed(sharingLabels = setOf(" ", "")) }
      case("allow labels, one of") { allowKids.isContentAllowed(sharingLabels = setOf("adult", "kids")) }

      val excludeGore = user(restrictions = ContentRestrictions(labelsExclude = setOf("gore")))
      case("exclude labels, none") { excludeGore.isContentAllowed() }
      case("exclude labels, match") { excludeGore.isContentAllowed(sharingLabels = setOf("Gore")) }
      case("exclude labels, other") { excludeGore.isContentAllowed(sharingLabels = setOf("kids")) }
      case("exclude labels, mixed") { excludeGore.isContentAllowed(sharingLabels = setOf("kids", "GORE ")) }

      val combined = ageRestricted(12, AllowExclude.ALLOW_ONLY, allow = setOf("kids"))
      case("allow age or label, both ok") { combined.isContentAllowed(10, setOf("kids")) }
      case("allow age or label, age ok") { combined.isContentAllowed(10, setOf("adult")) }
      case("allow age or label, label ok") { combined.isContentAllowed(18, setOf("kids")) }
      case("allow age or label, none ok") { combined.isContentAllowed(18, setOf("adult")) }
      case("allow age or label, null age, label ok") { combined.isContentAllowed(null, setOf("kids")) }
      case("allow age or label, nothing") { combined.isContentAllowed() }

      val excludeBoth = ageRestricted(16, AllowExclude.EXCLUDE, exclude = setOf("gore"))
      case("exclude age and label, clean") { excludeBoth.isContentAllowed(10, setOf("kids")) }
      case("exclude age and label, age") { excludeBoth.isContentAllowed(16, setOf("kids")) }
      case("exclude age and label, label") { excludeBoth.isContentAllowed(10, setOf("gore")) }

      val allowAndExclude = ageRestricted(12, AllowExclude.ALLOW_ONLY, allow = setOf("kids"), exclude = setOf("gore"))
      case("allow then exclude label") { allowAndExclude.isContentAllowed(10, setOf("kids", "gore")) }
      case("allow label excluded too") { user(restrictions = ContentRestrictions(labelsAllow = setOf("a"), labelsExclude = setOf("a"))).isContentAllowed(sharingLabels = setOf("a")) }
      case("allow age, exclude labels") { ageRestricted(12, AllowExclude.ALLOW_ONLY, exclude = setOf("gore")).isContentAllowed(10, setOf("x")) }
      case("exclude age, allow labels") { ageRestricted(16, AllowExclude.EXCLUDE, allow = setOf("kids")).isContentAllowed(10, setOf("adult")) }
      case("exclude age, allow labels, null age") { ageRestricted(16, AllowExclude.EXCLUDE, allow = setOf("kids")).isContentAllowed(null, setOf("kids")) }
      case("unicode labels") { user(restrictions = ContentRestrictions(labelsAllow = setOf("émile"))).isContentAllowed(sharingLabels = setOf("ÉMILE")) }
    }

    func("toString") {
      case("default") { user().toString() }
      case("full") {
        KomgaUser(
          "a'b@x.org",
          "pwd",
          setOf(UserRoles.ADMIN, UserRoles.KOBO_SYNC),
          setOf("L2", "L1"),
          false,
          ContentRestrictions(AgeRestriction(10, AllowExclude.EXCLUDE), setOf("b"), setOf("c")),
          "ID",
          LocalDateTime.of(2021, 1, 1, 0, 0),
          LocalDateTime.of(2022, 2, 2, 2, 2, 2, 2),
        ).toString()
      }
      case("password not shown") { user().toString().contains("secret") }
    }
  }
}
