package org.gotson.komga.oracle.interfaces.api.rest

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.AuthenticationActivity
import org.gotson.komga.domain.model.DuplicateNameException
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserEmailAlreadyExistsException
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.interfaces.api.rest.UserController
import org.gotson.komga.interfaces.api.rest.dto.ApiKeyRequestDto
import org.gotson.komga.interfaces.api.rest.dto.PasswordUpdateDto
import org.gotson.komga.interfaces.api.rest.dto.UserCreationDto
import org.gotson.komga.interfaces.api.rest.dto.UserUpdateDto
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.FIXED
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.principal
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.env.MockEnvironment
import java.time.LocalDateTime

class UserControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = RestOracle.Calls()

  /** Records the calls, answers like the TypeScript twin's fake */
  private val lifecycle =
    mockk<KomgaUserLifecycle> {
      every { updatePassword(any(), any(), any()) } answers { calls.add("updatePassword", firstArg<KomgaUser>().id, secondArg<String>(), thirdArg<Boolean>()) }
      every { createUser(any()) } answers {
        val u = firstArg<KomgaUser>()
        calls.add("createUser", u)
        if (u.email == "dup@example.org") throw UserEmailAlreadyExistsException("exists", "ERR_EXISTS")
        u
      }
      every { deleteUser(any()) } answers { calls.add("deleteUser", firstArg<KomgaUser>().id) }
      every { updateUser(any()) } answers { calls.add("updateUser", firstArg<KomgaUser>()) }
      every { createApiKey(any(), any()) } answers {
        val u = firstArg<KomgaUser>()
        val comment = secondArg<String>()
        calls.add("createApiKey", u.id, comment)
        when (comment) {
          "dup" -> throw DuplicateNameException("duplicate", "ERR_1034")
          "none" -> null
          else -> ApiKey(id = "K-$comment", userId = u.id, key = "KEYVALUE", comment = comment, createdDate = FIXED)
        }
      }
    }

  private fun controller(demo: Boolean = false) =
    UserController(lifecycle, db.komgaUserDao, db.libraryDao, db.authenticationActivityDao, MockEnvironment().apply { if (demo) setActiveProfiles("demo") })

  private val c = controller()
  private val demo = controller(true)
  private val admin = principal(RestSamples.admin)
  private val all = principal(RestSamples.all)

  private fun activity(
    user: KomgaUser?,
    day: Int,
    success: Boolean,
    apiKeyId: String? = null,
  ) = AuthenticationActivity(
    userId = user?.id,
    email = user?.email,
    apiKeyId = apiKeyId,
    ip = "10.0.0.$day",
    userAgent = "agent",
    success = success,
    error = if (success) null else "bad",
    dateTime = LocalDateTime.of(2021, 3, day, 12, 0),
    source = "Password",
  )

  override fun cases() {
    func("getCurrentUser") {
      case("admin") { c.getCurrentUser(admin, null) }
      case("remember me") { c.getCurrentUser(principal(RestSamples.kids), true) }
    }
    func("getUsers") {
      case("empty") { c.getUsers() }
      case("seeded") {
        RestSamples.seed(db)
        c.getUsers()
      }
    }
    func("updatePasswordForCurrentUser") {
      case("ok") {
        c.updatePasswordForCurrentUser(all, PasswordUpdateDto("newpass"))
        calls.take()
      }
      case("email case insensitive") {
        c.updatePasswordForCurrentUser(principal(RestSamples.all.copy(email = "ALL@EXAMPLE.ORG")), PasswordUpdateDto("x"))
        calls.take()
      }
      case("unknown user") { listOf(exceptionType { c.updatePasswordForCurrentUser(principal(RestOracle.user("NOPE")), PasswordUpdateDto("x")) }, calls.take()) }
      case("demo") { demo.updatePasswordForCurrentUser(admin, PasswordUpdateDto("x")) }
    }
    func("addUser") {
      case("minimal") { listOf(stable(c.addUser(read<UserCreationDto>("""{"email":"new@example.org","password":"p"}"""))), calls.take()) }
      case("roles, unknown ignored") {
        listOf(stable(c.addUser(read<UserCreationDto>("""{"email":"r@example.org","password":"p","roles":["PAGE_STREAMING","NOPE","ADMIN"]}"""))), calls.take())
      }
      case("restricted libraries, unknown filtered") {
        listOf(
          stable(c.addUser(read<UserCreationDto>("""{"email":"s@example.org","password":"p","sharedLibraries":{"all":false,"libraryIds":["L2","LX"]}}"""))),
          calls.take(),
        )
      }
      case("shared all") { stable(c.addUser(read<UserCreationDto>("""{"email":"a@example.org","password":"p","sharedLibraries":{"all":true,"libraryIds":["L2"]}}"""))) }
      case("restrictions") {
        listOf(
          stable(
            c.addUser(
              read<UserCreationDto>(
                """{"email":"k@example.org","password":"p","ageRestriction":{"age":12,"restriction":"EXCLUDE"},"labelsAllow":["b","a"],"labelsExclude":["x"]}""",
              ),
            ),
          ),
          calls.take(),
        )
      }
      case("age restriction NONE") { stable(c.addUser(read<UserCreationDto>("""{"email":"n@example.org","password":"p","ageRestriction":{"age":12,"restriction":"NONE"}}"""))) }
      case("duplicate email") { listOf(c.addUser(read<UserCreationDto>("""{"email":"dup@example.org","password":"p"}""")), calls.take()) }
    }
    func("deleteUserById") {
      case("existing") {
        c.deleteUserById("KIDS", admin)
        calls.take()
      }
      case("unknown") { listOf(c.deleteUserById("NOPE", admin), calls.take()) }
    }
    func("updateUserById") {
      case("empty patch") {
        c.updateUserById("NOADULT", read<UserUpdateDto>("{}"), admin)
        calls.take()
      }
      case("roles and libraries") {
        c.updateUserById("NOADULT", read<UserUpdateDto>("""{"roles":["FILE_DOWNLOAD","BAD"],"sharedLibraries":{"all":false,"libraryIds":["L1","LX"]}}"""), admin)
        calls.take()
      }
      case("shared all clears ids") {
        c.updateUserById("L1ONLY", read<UserUpdateDto>("""{"sharedLibraries":{"all":true,"libraryIds":["L1"]}}"""), admin)
        calls.take()
      }
      case("restrictions set") {
        c.updateUserById("ALL", read<UserUpdateDto>("""{"ageRestriction":{"age":16,"restriction":"ALLOW_ONLY"},"labelsAllow":["a"],"labelsExclude":["b"]}"""), admin)
        calls.take()
      }
      case("restrictions cleared with null") {
        c.updateUserById("KIDS", read<UserUpdateDto>("""{"ageRestriction":null,"labelsAllow":null,"labelsExclude":null}"""), admin)
        calls.take()
      }
      case("age restriction NONE") {
        c.updateUserById("KIDS", read<UserUpdateDto>("""{"ageRestriction":{"age":3,"restriction":"NONE"}}"""), admin)
        calls.take()
      }
      case("roles null") { listOf(exceptionType { c.updateUserById("ALL", read<UserUpdateDto>("""{"roles":null}"""), admin) }, calls.take()) }
      case("unknown") { listOf(c.updateUserById("NOPE", read<UserUpdateDto>("{}"), admin), calls.take()) }
    }
    func("updatePasswordByUserId") {
      case("admin for other") {
        c.updatePasswordByUserId("ALL", admin, PasswordUpdateDto("p2"))
        calls.take()
      }
      case("self") {
        c.updatePasswordByUserId("ALL", all, PasswordUpdateDto("p3"))
        calls.take()
      }
      case("unknown") { c.updatePasswordByUserId("NOPE", admin, PasswordUpdateDto("p")) }
      case("demo") { demo.updatePasswordByUserId("ALL", admin, PasswordUpdateDto("p")) }
    }
    func("getAuthenticationActivityForCurrentUser") {
      case("empty") { c.getAuthenticationActivityForCurrentUser(all, false, PageRequest.of(0, 20)) }
      case("default sort dateTime desc") {
        db.authenticationActivityDao.insert(activity(RestSamples.all, 1, true))
        db.authenticationActivityDao.insert(activity(RestSamples.all, 3, false))
        db.authenticationActivityDao.insert(activity(RestSamples.all, 2, true, "K1"))
        db.authenticationActivityDao.insert(activity(RestSamples.admin, 4, true))
        db.authenticationActivityDao.insert(activity(null, 5, false))
        RestOracle.sql(db, "update AUTHENTICATION_ACTIVITY set DATE_TIME = '2021-03-0' || substr(IP, 8, 1) || ' 12:00:00'")
        c.getAuthenticationActivityForCurrentUser(all, false, PageRequest.of(0, 20))
      }
      case("sorted by ip asc") { c.getAuthenticationActivityForCurrentUser(all, false, PageRequest.of(0, 20, Sort.by("ip"))) }
      case("paged") { c.getAuthenticationActivityForCurrentUser(all, false, PageRequest.of(1, 2)) }
      case("unpaged keeps sort") { c.getAuthenticationActivityForCurrentUser(all, true, PageRequest.of(1, 1, Sort.by(Sort.Order.asc("success"), Sort.Order.desc("dateTime")))) }
      case("unpaged default sort") { c.getAuthenticationActivityForCurrentUser(all, true, Pageable.unpaged()) }
      case("demo non admin") { demo.getAuthenticationActivityForCurrentUser(all, false, PageRequest.of(0, 20)) }
      case("demo admin") { demo.getAuthenticationActivityForCurrentUser(admin, false, PageRequest.of(0, 20)) }
    }
    func("getAuthenticationActivity") {
      case("all") { c.getAuthenticationActivity(false, PageRequest.of(0, 20)) }
      case("sorted by email") { c.getAuthenticationActivity(false, PageRequest.of(0, 3, Sort.by(Sort.Order.desc("email")))) }
      case("unpaged") { c.getAuthenticationActivity(true, PageRequest.of(0, 1)) }
    }
    func("getLatestAuthenticationActivityByUserId") {
      case("latest") { c.getLatestAuthenticationActivityByUserId("ALL", all, null) }
      case("by api key") { c.getLatestAuthenticationActivityByUserId("ALL", all, "K1") }
      case("unknown api key") { c.getLatestAuthenticationActivityByUserId("ALL", all, "K9") }
      case("no activity") { c.getLatestAuthenticationActivityByUserId("KIDS", admin, null) }
      case("unknown user") { c.getLatestAuthenticationActivityByUserId("NOPE", admin, null) }
    }
    func("getApiKeysForCurrentUser") {
      case("none") { c.getApiKeysForCurrentUser(all) }
      case("redacted") {
        db.komgaUserDao.insert(ApiKey(id = "K1", userId = "ALL", key = "secret1", comment = "one", createdDate = LocalDateTime.of(2021, 1, 1, 0, 0)))
        db.komgaUserDao.insert(ApiKey(id = "K2", userId = "ALL", key = "secret2", comment = "two", createdDate = LocalDateTime.of(2021, 7, 1, 0, 0)))
        db.komgaUserDao.insert(ApiKey(id = "K3", userId = "ADMIN", key = "secret3", comment = "admin", createdDate = LocalDateTime.of(2021, 1, 1, 0, 0)))
        RestOracle.fixNow(db)
        c.getApiKeysForCurrentUser(all)
      }
      case("demo non admin") { demo.getApiKeysForCurrentUser(all) }
      case("demo admin") { demo.getApiKeysForCurrentUser(admin) }
    }
    func("createApiKeyForCurrentUser") {
      case("created, not redacted") { listOf(c.createApiKeyForCurrentUser(all, ApiKeyRequestDto("new")), calls.take()) }
      case("duplicate") { listOf(c.createApiKeyForCurrentUser(all, ApiKeyRequestDto("dup")), calls.take()) }
      case("generation failed") { listOf(c.createApiKeyForCurrentUser(all, ApiKeyRequestDto("none")), calls.take()) }
      case("demo non admin") { listOf(demo.createApiKeyForCurrentUser(all, ApiKeyRequestDto("x")), calls.take()) }
      case("demo admin") { listOf(demo.createApiKeyForCurrentUser(admin, ApiKeyRequestDto("x")), calls.take()) }
    }
    func("deleteApiKeyByKeyId") {
      case("own key") {
        c.deleteApiKeyByKeyId(all, "K1")
        db.komgaUserDao.findApiKeyByUserId("ALL").map { it.id }
      }
      case("other user's key") { listOf(c.deleteApiKeyByKeyId(all, "K3"), db.komgaUserDao.findApiKeyByUserId("ADMIN").map { it.id }) }
      case("unknown key") { c.deleteApiKeyByKeyId(all, "K1") }
    }
  }
}
