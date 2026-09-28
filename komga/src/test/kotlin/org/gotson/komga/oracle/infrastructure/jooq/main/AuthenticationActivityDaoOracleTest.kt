package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.AuthenticationActivity
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.sql
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed.user
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class AuthenticationActivityDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.authenticationActivityDao

  private val u1 = user("U1", "alice@example.org")
  private val u2 = user("U2", "Bob@Example.org")
  private val ghost = user("GHOST", "ghost@example.org")

  private fun emails(p: Pageable) = dao.findAll(p).content.map { listOf(it.email, it.ip, it.dateTime) }

  override fun cases() {
    func("findAll@42") {
      case("empty database, unpaged") { dao.findAll(Pageable.unpaged()) }
      case("empty database, paged") { dao.findAll(PageRequest.of(0, 5)) }
    }

    func("insert") {
      case("all fields") {
        db.komgaUserDao.insert(u1)
        db.komgaUserDao.insert(u2)
        dao.insert(
          AuthenticationActivity(
            userId = "U1",
            email = "alice@example.org",
            apiKeyId = "K1",
            apiKeyComment = "Kobo ünïcode",
            ip = "192.168.0.1",
            userAgent = "Mozilla/5.0",
            success = true,
            error = null,
            source = "ApiKey",
          ),
        )
        stable(dao.findAll(Pageable.unpaged()))
      }
      case("failed login without user") {
        dao.insert(AuthenticationActivity(email = "unknown@example.org", ip = "10.0.0.1", userAgent = "curl", success = false, error = "Bad credentials", source = "Password"))
        dao.findAll(Pageable.unpaged()).totalElements
      }
      case("only mandatory fields") {
        dao.insert(AuthenticationActivity(success = false))
        db.rawQuery("select USER_ID, EMAIL, API_KEY_ID, API_KEY_COMMENT, IP, USER_AGENT, SUCCESS, ERROR, SOURCE from AUTHENTICATION_ACTIVITY order by rowid")
      }
      case("date time is not inserted") {
        dao.insert(AuthenticationActivity(userId = "U2", email = "bob@example.org", success = true, dateTime = LocalDateTime.of(2000, 1, 1, 0, 0), source = "Password"))
        stable(db.rawQuery("select DATE_TIME >= '2001' from AUTHENTICATION_ACTIVITY order by rowid"))
      }
      case("unknown user id") { exceptionType { dao.insert(AuthenticationActivity(userId = "NOPE", success = true)) } }
      case("more activities") {
        dao.insert(AuthenticationActivity(userId = "U1", email = "alice@example.org", ip = "192.168.0.2", success = true, source = "Password"))
        dao.insert(AuthenticationActivity(userId = null, email = "ALICE@example.org", ip = "192.168.0.3", success = false, error = "Bad credentials", source = "Password"))
        dao.insert(AuthenticationActivity(userId = "U2", email = "bob@example.org", apiKeyId = "K2", ip = "::1", userAgent = "KOReader", success = true, source = "ApiKey"))
        dao.insert(AuthenticationActivity(userId = null, email = "Bob@Example.org", ip = "192.168.0.4", userAgent = "Émoji 😀", success = false, error = "Utilisateur désactivé", source = "Password"))
        dao.insert(AuthenticationActivity(userId = "U1", email = "alice@example.org", apiKeyId = "K1", ip = "192.168.0.5", success = true, source = "ApiKey"))
        // fixed, distinct dates (rowid order), far from today
        sql(db, "update AUTHENTICATION_ACTIVITY set DATE_TIME = datetime('2021-06-01 08:00:00', '+' || (rowid * 7 % 11) || ' hours')")
        db.rawQuery("select rowid, DATE_TIME from AUTHENTICATION_ACTIVITY order by rowid")
      }
    }

    func("findAll@42") {
      case("unpaged") { dao.findAll(Pageable.unpaged()) }
      case("unpaged sorted") { dao.findAll(Pageable.unpaged(Sort.by("ip"))).content.map { it.ip } }
      case("first page") { dao.findAll(PageRequest.of(0, 3)) }
      case("second page sorted by date desc") { dao.findAll(PageRequest.of(1, 3, Sort.by(Sort.Direction.DESC, "dateTime"))) }
      case("last partial page") { dao.findAll(PageRequest.of(2, 3, Sort.by("dateTime"))) }
      case("page beyond the end") { dao.findAll(PageRequest.of(10, 3, Sort.by("dateTime"))) }
      case("sorted by email") { emails(PageRequest.of(0, 20, Sort.by("email"))) }
      case("sorted by email desc") { emails(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "email"))) }
      case("sorted by success then ip") { emails(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("success"), Sort.Order.asc("ip")))) }
      case("sorted by error") { emails(PageRequest.of(0, 20, Sort.by("error", "dateTime"))) }
      case("sorted by user id") { emails(PageRequest.of(0, 20, Sort.by("userId", "dateTime"))) }
      case("sorted by user agent desc") { emails(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "userAgent", "dateTime"))) }
      case("unknown sort property is ignored") { dao.findAll(PageRequest.of(0, 2, Sort.by("source"))) }
      case("unknown and known sort properties") { emails(PageRequest.of(0, 20, Sort.by("nope", "dateTime"))) }
    }

    func("findAllByUser") {
      case("by id or email") { dao.findAllByUser(u1, Pageable.unpaged()) }
      case("email is case sensitive") { dao.findAllByUser(u2, PageRequest.of(0, 10, Sort.by("dateTime"))) }
      case("paged") { dao.findAllByUser(u1, PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "dateTime"))) }
      case("user without activity") { dao.findAllByUser(ghost, Pageable.unpaged()) }
      case("unknown id but known email") { dao.findAllByUser(user("NOPE", "bob@example.org"), Pageable.unpaged()).content.map { it.ip } }
    }

    func("findAll@69") {
      case("count with a condition") { dao.findAllByUser(u2, PageRequest.of(0, 1)).let { listOf(it.totalElements, it.totalPages, it.content.size) } }
      case("unpaged size is at least 20") { dao.findAllByUser(u1, Pageable.unpaged()).size }
    }

    func("findMostRecentByUser") {
      case("without api key") { dao.findMostRecentByUser(u1, null) }
      case("with api key") { dao.findMostRecentByUser(u1, "K1") }
      case("with unknown api key") { dao.findMostRecentByUser(u1, "NOPE") }
      case("api key of another user") { dao.findMostRecentByUser(u1, "K2") }
      case("second user") { dao.findMostRecentByUser(u2, null) }
      case("user without activity") { dao.findMostRecentByUser(ghost, null) }
      case("user without activity with api key") { dao.findMostRecentByUser(ghost, "K1") }
    }

    func("toDomain") {
      case("date time in current time zone") { dao.findAll(PageRequest.of(0, 20, Sort.by("dateTime"))).content.map { it.dateTime } }
      case("stored values") { db.rawQuery("select USER_ID, SUCCESS, DATE_TIME, SOURCE from AUTHENTICATION_ACTIVITY order by DATE_TIME") }
    }

    func("deleteOlderThan") {
      case("nothing older") {
        dao.deleteOlderThan(LocalDateTime.of(2000, 1, 1, 0, 0))
        dao.findAll(Pageable.unpaged()).totalElements
      }
      case("some older") {
        dao.deleteOlderThan(LocalDateTime.of(2021, 6, 1, 12, 0))
        db.rawQuery("select DATE_TIME from AUTHENTICATION_ACTIVITY order by DATE_TIME")
      }
      case("boundary is exclusive") {
        dao.deleteOlderThan(LocalDateTime.of(2021, 6, 1, 13, 0))
        db.rawQuery("select DATE_TIME from AUTHENTICATION_ACTIVITY order by DATE_TIME")
      }
    }

    func("deleteByUser") {
      case("user without activity") {
        dao.deleteByUser(ghost)
        dao.findAll(Pageable.unpaged()).totalElements
      }
      case("by id or email") {
        dao.deleteByUser(u2)
        db.rawQuery("select USER_ID, EMAIL from AUTHENTICATION_ACTIVITY order by DATE_TIME")
      }
      case("all") {
        dao.deleteByUser(u1)
        dao.deleteByUser(user("X", "ALICE@example.org"))
        dao.findAll(Pageable.unpaged())
      }
    }
  }
}
