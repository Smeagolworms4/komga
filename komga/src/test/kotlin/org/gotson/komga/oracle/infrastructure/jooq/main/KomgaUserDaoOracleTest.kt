package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.T0
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.library
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.transactional
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.user

class KomgaUserDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.komgaUserDao

  private val admin =
    KomgaUser(
      email = "Admin@Example.org",
      password = "\$2a\$10\$hash",
      roles = UserRoles.entries.toSet(),
      sharedLibrariesIds = setOf("L2", "L1"),
      sharedAllLibraries = false,
      restrictions =
        ContentRestrictions(
          ageRestriction = AgeRestriction(16, AllowExclude.ALLOW_ONLY),
          labelsAllow = setOf("Kids", "ÜNÏ", "adult"),
          labelsExclude = setOf("Adult", " "),
        ),
      id = "U2",
      createdDate = T0,
    )

  private val restricted =
    KomgaUser(
      email = "Élodie@Exemple.fr",
      password = "p",
      roles = emptySet(),
      sharedLibrariesIds = setOf("L3"),
      sharedAllLibraries = false,
      restrictions = ContentRestrictions(ageRestriction = AgeRestriction(0, AllowExclude.EXCLUDE), labelsExclude = setOf("gore")),
      id = "U3",
      createdDate = T0,
    )

  private fun key(
    id: String,
    userId: String,
    key: String,
    comment: String,
  ) = ApiKey(id = id, userId = userId, key = key, comment = comment, createdDate = T0)

  private fun users() = stable(dao.findAll().sortedBy { it.id })

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }

    func("findAll") {
      case("empty") { dao.findAll() }
    }

    func("insert@104") {
      case("defaults") {
        (1..3).forEach { db.libraryDao.insert(library("L$it")) }
        dao.insert(user("U1"))
        stable(dao.findByIdOrNull("U1"))
      }
      case("all fields") {
        dao.insert(admin)
        stable(dao.findByIdOrNull("U2"))
      }
      case("exclude restriction without roles") {
        dao.insert(restricted)
        stable(dao.findByIdOrNull("U3"))
      }
      case("duplicate id") { exceptionType { dao.insert(user("U1", "other@example.org")) } }
      case("duplicate email") { exceptionType { dao.insert(user("U9", "U1@example.org")) } }
      case("email unique constraint is case sensitive") {
        dao.insert(user("U4", "u1@EXAMPLE.org"))
        dao.count()
      }
      case("unknown shared library") { exceptionType { transactional(db) { dao.insert(user("U5").copy(sharedLibrariesIds = setOf("NOPE"))) } } }
      case("after a failed insert") { db.rawQuery("select ID, EMAIL from USER order by ID") }
    }

    func("insertRoles") {
      case("rows") { db.rawQuery("select USER_ID, ROLE from USER_ROLE order by USER_ID, ROLE") }
    }

    func("insertSharedLibraries") {
      case("rows") { db.rawQuery("select USER_ID, LIBRARY_ID from USER_LIBRARY_SHARING order by USER_ID, LIBRARY_ID") }
    }

    func("insertSharingRestrictions") {
      case("rows") { db.rawQuery("select USER_ID, ALLOW, LABEL from USER_SHARING order by USER_ID, ALLOW, LABEL") }
      case("stored user values") { db.rawQuery("select ID, SHARED_ALL_LIBRARIES, AGE_RESTRICTION, AGE_RESTRICTION_ALLOW_ONLY from USER order by ID") }
    }

    func("findAll") {
      case("all users") { users() }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("U3")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("case sensitive") { dao.findByIdOrNull("u1") }
    }

    func("selectBase") {
      case("one user per shared library set") { dao.findAll().map { it.id to it.sharedLibrariesIds }.sortedBy { it.first } }
    }

    func("fetchAndMap") {
      case("unknown stored role is ignored") {
        sql(db, "insert into USER_ROLE (USER_ID, ROLE) values ('U1', 'SUPERUSER'), ('U1', 'admin')")
        dao.findByIdOrNull("U1")!!.roles
      }
      case("age restriction without allow only flag") {
        sql(db, "update USER set AGE_RESTRICTION = 12, AGE_RESTRICTION_ALLOW_ONLY = null where ID = 'U4'")
        dao.findByIdOrNull("U4")!!.restrictions
      }
      case("allow only flag without age") {
        sql(db, "update USER set AGE_RESTRICTION = null, AGE_RESTRICTION_ALLOW_ONLY = 1 where ID = 'U4'")
        dao.findByIdOrNull("U4")!!.restrictions
      }
      case("stored labels are not normalized") {
        sql(db, "insert into USER_SHARING (USER_ID, ALLOW, LABEL) values ('U4', 1, 'Mixed Case'), ('U4', 0, 'Mixed Case')")
        dao.findByIdOrNull("U4")!!.restrictions.let { listOf(it.labelsAllow, it.labelsExclude) }
      }
      case("dates in current time zone") {
        sql(db, "update USER set CREATED_DATE = '2020-03-29 01:30:00', LAST_MODIFIED_DATE = '2020-10-25 00:30:00' where ID = 'U4'")
        dao.findByIdOrNull("U4")!!.let { listOf(it.createdDate, it.lastModifiedDate) }
      }
    }

    func("insert@126") {
      case("api key") {
        dao.insert(key("K1", "U1", "secret-key-1", "Kobo"))
        stable(dao.findApiKeyByUserId("U1"))
      }
      case("several keys") {
        dao.insert(key("K2", "U2", "secret-key-2", "Ünïcode Commentaire"))
        dao.insert(key("K3", "U2", "secret-key-3", ""))
        dao.findApiKeyByUserId("U2").map { it.id }
      }
      case("duplicate key value") { exceptionType { dao.insert(key("K4", "U2", "secret-key-2", "dup")) } }
      case("duplicate id") { exceptionType { dao.insert(key("K1", "U2", "other", "dup")) } }
      case("unknown user") { exceptionType { dao.insert(key("K5", "NOPE", "k5", "c")) } }
      case("generated dates") {
        dao.insert(ApiKey(id = "K6", userId = "U3", key = "secret-key-6", comment = "now"))
        stable(dao.findApiKeyByUserId("U3"))
      }
    }

    func("findApiKeyByUserId") {
      case("user with keys") { stable(dao.findApiKeyByUserId("U2")) }
      case("user without key") { dao.findApiKeyByUserId("U4") }
      case("missing user") { dao.findApiKeyByUserId("NOPE") }
    }

    func("toDomain") {
      case("stored api key dates") {
        sql(db, "update USER_API_KEY set CREATED_DATE = '2021-07-01 12:00:00', LAST_MODIFIED_DATE = '2021-12-01 12:00:00' where ID = 'K6'")
        dao.findApiKeyByUserId("U3")
      }
    }

    func("existsApiKeyByIdAndUserId") {
      case("existing") { dao.existsApiKeyByIdAndUserId("K1", "U1") }
      case("other user") { dao.existsApiKeyByIdAndUserId("K1", "U2") }
      case("missing") { dao.existsApiKeyByIdAndUserId("NOPE", "U1") }
      case("case sensitive id") { dao.existsApiKeyByIdAndUserId("k1", "U1") }
    }

    func("existsApiKeyByCommentAndUserId") {
      case("exact") { dao.existsApiKeyByCommentAndUserId("Kobo", "U1") }
      case("ignore ascii case") { dao.existsApiKeyByCommentAndUserId("kOBO", "U1") }
      case("unicode case") { dao.existsApiKeyByCommentAndUserId("üNÏCODE COMMENTAIRE", "U2") }
      case("unicode same case") { dao.existsApiKeyByCommentAndUserId("Ünïcode commentaire", "U2") }
      case("empty comment") { dao.existsApiKeyByCommentAndUserId("", "U2") }
      case("other user") { dao.existsApiKeyByCommentAndUserId("Kobo", "U2") }
    }

    func("existsByEmailIgnoreCase") {
      case("exact") { dao.existsByEmailIgnoreCase("Admin@Example.org") }
      case("ignore ascii case") { dao.existsByEmailIgnoreCase("ADMIN@EXAMPLE.ORG") }
      case("unicode case") { dao.existsByEmailIgnoreCase("élodie@exemple.fr") }
      case("unicode same case") { dao.existsByEmailIgnoreCase("Élodie@exemple.FR") }
      case("missing") { dao.existsByEmailIgnoreCase("nobody@example.org") }
      case("empty") { dao.existsByEmailIgnoreCase("") }
      case("like wildcard is literal") { dao.existsByEmailIgnoreCase("%@example.org") }
    }

    func("findByEmailIgnoreCaseOrNull") {
      case("ignore ascii case") { stable(dao.findByEmailIgnoreCaseOrNull("admin@example.ORG")) }
      case("several matches") { dao.findByEmailIgnoreCaseOrNull("U1@example.org")?.id }
      case("unicode case") { dao.findByEmailIgnoreCaseOrNull("élodie@exemple.fr") }
      case("missing") { dao.findByEmailIgnoreCaseOrNull("nobody@example.org") }
    }

    func("findByApiKeyOrNull") {
      case("existing") { stable(dao.findByApiKeyOrNull("secret-key-2")) }
      case("user with several keys and libraries") { dao.findByApiKeyOrNull("secret-key-3")?.let { listOf(it.first.id, it.first.sharedLibrariesIds, it.second.id) } }
      case("missing") { dao.findByApiKeyOrNull("NOPE") }
      case("case sensitive") { dao.findByApiKeyOrNull("SECRET-KEY-1") }
      case("empty") { dao.findByApiKeyOrNull("") }
    }

    func("saveAnnouncementIdsRead") {
      case("several") {
        dao.saveAnnouncementIdsRead(admin, setOf("https://komga.org/blog/b", "https://komga.org/blog/a"))
        dao.findAnnouncementIdsReadByUserId("U2")
      }
      case("already read are ignored") {
        dao.saveAnnouncementIdsRead(admin, setOf("https://komga.org/blog/a", "ünïcode"))
        dao.findAnnouncementIdsReadByUserId("U2")
      }
      case("empty") {
        dao.saveAnnouncementIdsRead(admin, emptySet())
        dao.findAnnouncementIdsReadByUserId("U2").size
      }
      case("other user") {
        dao.saveAnnouncementIdsRead(user("U1"), setOf("https://komga.org/blog/a"))
        dao.findAnnouncementIdsReadByUserId("U1")
      }
      case("unknown user") { exceptionType { dao.saveAnnouncementIdsRead(user("NOPE"), setOf("x")) } }
    }

    func("findAnnouncementIdsReadByUserId") {
      case("user without announcement") { dao.findAnnouncementIdsReadByUserId("U3") }
      case("missing user") { dao.findAnnouncementIdsReadByUserId("NOPE") }
    }

    func("update") {
      case("all fields") {
        dao.update(
          admin.copy(
            email = "new@example.org",
            password = "new",
            roles = setOf(UserRoles.KOBO_SYNC),
            sharedLibrariesIds = setOf("L3"),
            sharedAllLibraries = true,
            restrictions = ContentRestrictions(ageRestriction = AgeRestriction(18, AllowExclude.EXCLUDE), labelsAllow = setOf("x")),
          ),
        )
        stable(dao.findByIdOrNull("U2"))
      }
      case("remove restrictions") {
        dao.update(dao.findByIdOrNull("U2")!!.copy(restrictions = ContentRestrictions(), roles = emptySet(), sharedLibrariesIds = emptySet()))
        stable(dao.findByIdOrNull("U2"))
      }
      case("created date is kept") { stable(dao.findByIdOrNull("U2")!!.createdDate) }
      case("api keys and announcements are kept") { listOf(dao.findApiKeyByUserId("U2").size, dao.findAnnouncementIdsReadByUserId("U2").size) }
      case("duplicate email") { exceptionType { dao.update(dao.findByIdOrNull("U2")!!.copy(email = "U1@example.org")) } }
      case("missing user") { exceptionType { transactional(db) { dao.update(user("NOPE")) } } }
      case("missing user without roles") {
        dao.update(user("NOPE").copy(roles = emptySet()))
        dao.findByIdOrNull("NOPE")
      }
    }

    func("deleteApiKeyByIdAndUserId") {
      case("other user") {
        dao.deleteApiKeyByIdAndUserId("K1", "U2")
        dao.findApiKeyByUserId("U1").size
      }
      case("existing") {
        dao.deleteApiKeyByIdAndUserId("K1", "U1")
        dao.findApiKeyByUserId("U1").size
      }
    }

    func("deleteApiKeyByUserId") {
      case("missing user") {
        dao.deleteApiKeyByUserId("NOPE")
        db.rawQuery("select count(*) from USER_API_KEY")
      }
      case("existing") {
        dao.deleteApiKeyByUserId("U2")
        db.rawQuery("select ID from USER_API_KEY order by ID")
      }
    }

    func("delete") {
      case("user with everything") {
        dao.insert(key("K7", "U3", "k7", "c"))
        dao.saveAnnouncementIdsRead(restricted, setOf("a"))
        dao.delete("U3")
        listOf(dao.findByIdOrNull("U3"), dao.count(), db.rawQuery("select (select count(*) from USER_API_KEY), (select count(*) from ANNOUNCEMENTS_READ), (select count(*) from USER_SHARING), (select count(*) from USER_LIBRARY_SHARING), (select count(*) from USER_ROLE)"))
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
      case("user with authentication activity") { exceptionType { sql(db, "insert into AUTHENTICATION_ACTIVITY (USER_ID, SUCCESS) values ('U4', 1)"); transactional(db) { dao.delete("U4") } } }
    }

    func("deleteAll") {
      case("with authentication activity") { listOf(exceptionType { transactional(db) { dao.deleteAll() } }, dao.count()) }
      case("all") {
        sql(db, "delete from AUTHENTICATION_ACTIVITY")
        dao.deleteAll()
        listOf(dao.count(), dao.findAll(), db.rawQuery("select (select count(*) from USER_API_KEY), (select count(*) from ANNOUNCEMENTS_READ), (select count(*) from USER_SHARING), (select count(*) from USER_LIBRARY_SHARING), (select count(*) from USER_ROLE)"))
      }
    }

    func("count") {
      case("after deletions") { dao.count() }
    }
  }
}
