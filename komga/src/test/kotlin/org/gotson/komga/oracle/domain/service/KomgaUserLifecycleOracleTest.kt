package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.AgeRestriction
import org.gotson.komga.domain.model.AllowExclude
import org.gotson.komga.domain.model.AuthenticationActivity
import org.gotson.komga.domain.model.ContentRestrictions
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.TokenEncoder
import org.gotson.komga.infrastructure.security.apikey.ApiKeyGenerator
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.springframework.security.core.session.SessionInformation
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Date

class KomgaUserLifecycleOracleTest : OracleTest() {
  /** Deterministic encoder (same fake in the TypeScript twin) */
  private class FakePasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence): String = "enc:$rawPassword"

    override fun matches(
      rawPassword: CharSequence,
      encodedPassword: String?,
    ): Boolean = encodedPassword == "enc:$rawPassword"
  }

  /** Sessions registered by user id (same fake in the TypeScript twin) */
  private class FakeSessionRegistry : SessionRegistry {
    val sessions = mutableListOf<Pair<String, SessionInformation>>()

    fun register(
      userId: String,
      sessionId: String,
    ) {
      sessions.add(userId to SessionInformation(userId, sessionId, Date(0)))
    }

    fun state() = sessions.map { listOf(it.first, it.second.sessionId, it.second.isExpired) }

    override fun getAllPrincipals(): MutableList<Any> = throw UnsupportedOperationException()

    override fun getAllSessions(
      principal: Any,
      includeExpiredSessions: Boolean,
    ): MutableList<SessionInformation> =
      sessions
        .filter { it.first == (principal as KomgaPrincipal).user.id && (includeExpiredSessions || !it.second.isExpired) }
        .map { it.second }
        .toMutableList()

    override fun getSessionInformation(sessionId: String): SessionInformation = throw UnsupportedOperationException()

    override fun refreshLastRequest(sessionId: String) = throw UnsupportedOperationException()

    override fun registerNewSession(
      sessionId: String,
      principal: Any,
    ) = throw UnsupportedOperationException()

    override fun removeSessionInformation(sessionId: String) = throw UnsupportedOperationException()
  }

  /** Returns the queued keys, then "key<n>" (same fake in the TypeScript twin) */
  private class FakeApiKeyGenerator : ApiKeyGenerator() {
    val queue = ArrayDeque<String>()
    var n = 0

    override fun generate(): String = queue.removeFirstOrNull() ?: "key${++n}"
  }

  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val sessions = FakeSessionRegistry()
  private val generator = FakeApiKeyGenerator()
  private val lifecycle =
    KomgaUserLifecycle(
      db.komgaUserDao,
      db.readProgressDao,
      db.authenticationActivityDao,
      db.syncPointDao,
      FakePasswordEncoder(),
      TokenEncoder { "tok:$it" },
      sessions,
      graph.transactionTemplate,
      graph.publisher,
      generator,
      db.clientSettingsDtoDao,
    )

  private val u1 = KomgaUser("u1@example.org", "p1", id = "U1", createdDate = date)
  private val u2 = KomgaUser("u2@example.org", "p2", roles = setOf(UserRoles.ADMIN), id = "U2", createdDate = date)

  private fun user(id: String) = db.komgaUserDao.findByIdOrNull(id)

  private fun state(id: String) = stable(listOf(user(id), sessions.state(), graph.takeEvents()))

  override fun cases() {
    func("countUsers") {
      case("empty") { lifecycle.countUsers() }
    }
    func("createUser") {
      case("new user, password encoded") { stable(lifecycle.createUser(u1)) }
      case("second user") { stable(lifecycle.createUser(u2)) }
      case("same email other case") { lifecycle.createUser(u1.copy(email = "U1@EXAMPLE.org", id = "U3")) }
      case("same id other email") { exceptionType { lifecycle.createUser(u1.copy(email = "other@example.org")) } }
      case("count") { lifecycle.countUsers() }
      case("sessions setup") {
        sessions.register("U1", "s1")
        sessions.register("U1", "s2")
        sessions.register("U2", "s3")
        sessions.state()
      }
    }
    func("updatePassword") {
      case("keep sessions") {
        lifecycle.updatePassword(user("U1")!!, "new", false)
        state("U1")
      }
      case("expire sessions") {
        lifecycle.updatePassword(user("U1")!!, "newer", true)
        state("U1")
      }
      case("empty password") {
        lifecycle.updatePassword(user("U2")!!, "", false)
        state("U2")
      }
      case("unknown user") {
        listOf(exceptionType { DaoSeed.transactional(db) { lifecycle.updatePassword(u1.copy(id = "U9"), "x", true) } }, stable(listOf(user("U9"), graph.takeEvents())))
      }
    }
    func("updateUser") {
      case("unknown user") { lifecycle.updateUser(u1.copy(id = "U9")) }
      case("email only, password kept") {
        sessions.register("U2", "s4")
        lifecycle.updateUser(user("U2")!!.copy(email = "u2b@example.org", password = "ignored"))
        state("U2")
      }
      case("roles changed") {
        lifecycle.updateUser(user("U2")!!.copy(roles = setOf(UserRoles.FILE_DOWNLOAD)))
        state("U2")
      }
      case("same roles other order") {
        sessions.register("U2", "s5")
        lifecycle.updateUser(user("U2")!!.copy(roles = setOf(UserRoles.FILE_DOWNLOAD)))
        state("U2")
      }
      case("restrictions changed") {
        lifecycle.updateUser(user("U2")!!.copy(restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY), setOf("kids"))))
        state("U2")
      }
      case("same restrictions") {
        sessions.register("U2", "s6")
        lifecycle.updateUser(user("U2")!!.copy(restrictions = ContentRestrictions(AgeRestriction(12, AllowExclude.ALLOW_ONLY), setOf("kids"))))
        state("U2")
      }
      case("shared libraries changed") {
        db.libraryDao.insert(library("L1"))
        lifecycle.updateUser(user("U2")!!.copy(sharedAllLibraries = false, sharedLibrariesIds = setOf("L1")))
        state("U2")
      }
      case("shared all libraries only") {
        sessions.register("U2", "s7")
        lifecycle.updateUser(user("U2")!!.copy(sharedAllLibraries = true))
        state("U2")
      }
    }
    func("expireSessions") {
      case("user with sessions") {
        sessions.register("U1", "s8")
        lifecycle.expireSessions(user("U1")!!)
        sessions.state()
      }
      case("user without session") {
        lifecycle.expireSessions(u1.copy(id = "U9"))
        sessions.state()
      }
    }
    func("createApiKey") {
      case("first key") { stable(listOf(lifecycle.createApiKey(user("U1")!!, "  my key  "), db.komgaUserDao.findApiKeyByUserId("U1"))) }
      case("same comment") { lifecycle.createApiKey(user("U1")!!, "my key") }
      case("same comment with spaces") { lifecycle.createApiKey(user("U1")!!, " my key\t") }
      case("same comment other user") { stable(lifecycle.createApiKey(user("U2")!!, "my key")) }
      case("same comment other case") { stable(lifecycle.createApiKey(user("U1")!!, "MY KEY")) }
      case("empty comment") { stable(lifecycle.createApiKey(user("U1")!!, "")) }
      case("duplicate key is retried") {
        generator.queue.addAll(listOf("key1", "key1", "fresh"))
        stable(lifecycle.createApiKey(user("U1")!!, "retried"))
      }
      case("always duplicate") {
        generator.queue.addAll(List(10) { "key1" })
        stable(listOf(lifecycle.createApiKey(user("U1")!!, "failing"), db.komgaUserDao.findApiKeyByUserId("U1").map { it.comment }))
      }
      case("unknown user") { exceptionType { lifecycle.createApiKey(u1.copy(id = "U9"), "x") } }
    }
    func("deleteUser") {
      case("with related data") {
        db.seriesDao.insert(series("S1", "L1"))
        db.bookDao.insert(book("B1", "S1", "L1"))
        db.readProgressDao.save(ReadProgress("B1", "U1", 1, false, date, createdDate = date))
        db.readProgressDao.save(ReadProgress("B1", "U2", 1, false, date, createdDate = date))
        db.authenticationActivityDao.insert(AuthenticationActivity(userId = "U1", email = "u1@example.org", success = true, dateTime = date))
        db.clientSettingsDtoDao.saveForUser("U1", "k", "v")
        db.clientSettingsDtoDao.saveForUser("U2", "k", "v")
        sessions.register("U1", "s9")
        lifecycle.deleteUser(user("U1")!!)
        stable(
          listOf(
            user("U1"),
            db.komgaUserDao.findApiKeyByUserId("U1"),
            db.rawQuery("select USER_ID, BOOK_ID from READ_PROGRESS"),
            db.rawQuery("select USER_ID from AUTHENTICATION_ACTIVITY"),
            db.rawQuery("select USER_ID, KEY from CLIENT_SETTINGS_USER"),
            sessions.state(),
            graph.takeEvents(),
          ),
        )
      }
      case("unknown user") {
        lifecycle.deleteUser(u1.copy(id = "U9"))
        stable(listOf(lifecycle.countUsers(), graph.takeEvents()))
      }
      case("count") { lifecycle.countUsers() }
    }
  }
}
