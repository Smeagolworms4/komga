package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationProvider
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationToken
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.infrastructure.security.apikey.ApiKeySupport.describeDetails
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails

class ApiKeyAuthenticationProviderOracleTest : OracleTest() {
  private val db = OracleDb()
  private val provider = ApiKeyAuthenticationProvider(db.komgaUserDao)

  private fun token(
    key: String,
    userAgent: String = "agent",
  ) = ApiKeyAuthenticationToken
    .unauthenticated(ApiKeySupport.hasher.computeHash(key), ApiKeySupport.tokenEncoder.encode(key))
    .apply { details = ApiKeySupport.detailsSource.buildDetails(WebOracle.request(headers = listOf("User-Agent" to userAgent))) }

  private fun describe(a: Authentication?): List<Any?>? =
    a?.let {
      val p = it.principal as KomgaPrincipal
      WebOracle.describeAuthentication(it)!! + listOf(p.user.id, p.apiKey?.id, p.apiKey?.comment, p.name, describeDetails(it.details))
    }

  private fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = ApiKeyAuthenticationProvider::class.java.declaredMethods.first { it.name == name }
    m.isAccessible = true
    return try {
      m.invoke(provider, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  override fun cases() {
    func("retrieveUser") {
      case("populate") { ApiKeySupport.populate(db) }
      case("known key") {
        val p = call("retrieveUser", "masked", token("key-one")) as KomgaPrincipal
        listOf(p.user.id, p.apiKey?.id, p.name, p.username, p.authorities.map { it.authority }.sorted())
      }
      case("second key of the same user") { (call("retrieveUser", "masked", token("key-two")) as KomgaPrincipal).apiKey?.comment }
      case("unknown key") { call("retrieveUser", "masked", token("nope")) }
      case("raw key is not accepted") { call("retrieveUser", "masked", ApiKeyAuthenticationToken.unauthenticated("masked", "key-one")) }
    }
    func("additionalAuthenticationChecks") {
      case("no-op") { call("additionalAuthenticationChecks", null, null) }
    }
    func("createSuccessAuthentication") {
      case("from principal") {
        val principal = call("retrieveUser", "masked", token("admin-key")) as UserDetails
        describe(call("createSuccessAuthentication", principal, token("admin-key", "Browser"), principal) as Authentication)
      }
      case("null authentication") {
        val principal = call("retrieveUser", "masked", token("admin-key")) as UserDetails
        WebOracle.describeAuthentication(call("createSuccessAuthentication", principal, null, principal) as Authentication)
      }
    }
    func("supports") {
      case("ApiKeyAuthenticationToken") { provider.supports(ApiKeyAuthenticationToken::class.java) }
      case("UsernamePasswordAuthenticationToken") { provider.supports(UsernamePasswordAuthenticationToken::class.java) }
      case("AnonymousAuthenticationToken") { provider.supports(AnonymousAuthenticationToken::class.java) }
      case("RememberMeAuthenticationToken") { provider.supports(RememberMeAuthenticationToken::class.java) }
    }
    func("createSuccessAuthentication") {
      case("authenticate known key") { describe(provider.authenticate(token("key-one"))) }
      case("authenticate admin key") { describe(provider.authenticate(token("admin-key", "Mozilla"))) }
      case("authenticate unknown key") { provider.authenticate(token("unknown")) }
      case("authenticate empty key") { provider.authenticate(token("")) }
    }
  }
}
