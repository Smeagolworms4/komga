package org.gotson.komga.oracle.infrastructure.security.oauth2

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.infrastructure.security.oauth2.KomgaOAuth2UserServiceConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import java.time.Instant
import java.time.LocalDateTime

class KomgaOAuth2UserServiceConfigurationOracleTest : OracleTest() {
  private val db = OracleDb()
  private val idp by lazy { FakeIdentityProvider() }
  private val created = mutableListOf<String>()
  private val lifecycle =
    mockk<KomgaUserLifecycle>().also {
      every { it.createUser(any()) } answers {
        val u = firstArg<KomgaUser>()
        created.add(u.email)
        u
      }
    }
  private val properties = KomgaProperties()
  private val config = KomgaOAuth2UserServiceConfiguration(db.komgaUserDao, lifecycle, properties)

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      idp.describeError(e)
    }

  private fun oauth2(
    id: String,
    vararg scopes: String,
  ) = attempt { idp.describe(config.oauth2UserService().loadUser(idp.request(idp.registration(id, "/user", "login", *scopes)))) }.let { listOf(it, idp.drain(), created.toList().also { created.clear() }) }

  private fun idToken(vararg claims: Pair<String, Any>) = OidcIdToken("id-token", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"), mapOf("sub" to "sub-1", *claims))

  private fun oidc(
    token: OidcIdToken,
    userInfo: String?,
    vararg scopes: String,
  ) = attempt {
    val registration = idp.registration("oidc", userInfo, "sub", *scopes)
    idp.describe(config.oidcUserService().loadUser(OidcUserRequest(registration, idp.accessToken(*scopes), token)))
  }.let { listOf(it, idp.drain(), created.toList().also { created.clear() }) }

  override fun cases() {
    func("oauth2UserService") {
      case("setup") { db.komgaUserDao.insert(KomgaUser("Existing@Example.org", "pw", id = "U1", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0))) }
      case("existing user") {
        idp.responses["/user"] = 200 to """{"login":"u","email":"existing@example.org"}"""
        oauth2("keycloak", "profile")
      }
      case("no email") {
        idp.responses["/user"] = 200 to """{"login":"u"}"""
        oauth2("keycloak", "profile")
      }
      case("unknown user, creation disabled") {
        idp.responses["/user"] = 200 to """{"login":"u","email":"new@example.org"}"""
        oauth2("keycloak")
      }
      case("unknown user, creation enabled") {
        properties.oauth2AccountCreation = true
        oauth2("KeyCloak")
      }
      case("github delegate") {
        idp.responses["/user"] = 200 to """{"login":"u","email":null}"""
        idp.responses["/user/emails"] = 200 to """[{"email":"existing@example.org","verified":true,"primary":true}]"""
        oauth2("GitHub", "user:email")
      }
      case("user info failure") {
        idp.responses["/user"] = 500 to "{}"
        oauth2("other")
      }
    }
    func("oidcUserService") {
      case("email in id token, verified") { oidc(idToken("email" to "existing@example.org", "email_verified" to true), null, "openid") }
      case("email not verified") { oidc(idToken("email" to "existing@example.org", "email_verified" to false), null, "openid") }
      case("verification missing") { oidc(idToken("email" to "existing@example.org"), null, "openid") }
      case("verification disabled") {
        properties.oidcEmailVerification = false
        oidc(idToken("email" to "existing@example.org"), null, "openid")
      }
      case("no email") { oidc(idToken(), null, "openid") }
      case("user info endpoint") {
        idp.responses["/userinfo"] = 200 to """{"sub":"sub-1","email":"new-oidc@example.org","name":"N"}"""
        oidc(idToken(), "/userinfo", "openid", "email")
      }
      case("user info subject mismatch") {
        idp.responses["/userinfo"] = 200 to """{"sub":"other","email":"x@example.org"}"""
        oidc(idToken(), "/userinfo", "openid", "email")
      }
      case("user info not requested for other scopes") { oidc(idToken("email" to "existing@example.org"), "/userinfo", "openid", "custom") }
      case("creation disabled") {
        properties.oauth2AccountCreation = false
        oidc(idToken("email" to "unknown@example.org"), null, "openid")
      }
    }
    func("tryCreateNewUser") {
      case("disabled") {
        try {
          val m = KomgaOAuth2UserServiceConfiguration::class.java.getDeclaredMethod("tryCreateNewUser", String::class.java)
          m.isAccessible = true
          m.invoke(config, "a@b.c")
        } catch (e: java.lang.reflect.InvocationTargetException) {
          idp.describeError(e.targetException)
        }
      }
      case("enabled") {
        properties.oauth2AccountCreation = true
        val m = KomgaOAuth2UserServiceConfiguration::class.java.getDeclaredMethod("tryCreateNewUser", String::class.java)
        m.isAccessible = true
        (m.invoke(config, "a@b.c") as KomgaUser).let { listOf(it.email, it.password.length, created.toList().also { created.clear() }) }
      }
      case("stop") { idp.stop() }
    }
  }
}
