package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.security.LoginListener
import org.gotson.komga.infrastructure.security.UserAgentWebAuthenticationDetailsSource
import org.gotson.komga.infrastructure.security.apikey.ApiKeyAuthenticationToken
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent
import org.springframework.security.authentication.event.AuthenticationFailureProviderNotFoundEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.authentication.WebAuthenticationDetails
import java.time.LocalDateTime

class LoginListenerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val listener = LoginListener(db.authenticationActivityDao, db.komgaUserDao)
  private val date = LocalDateTime.of(2020, 5, 6, 7, 8, 9)
  private val user = KomgaUser("user@example.org", "pw", id = "U1", createdDate = date)
  private val apiKey = ApiKey(id = "K1", userId = "U1", key = "hashed", comment = "My Kobo", createdDate = date)
  private val roles = listOf(SimpleGrantedAuthority("ROLE_USER"))
  private val details = UserAgentWebAuthenticationDetailsSource().buildDetails(WebOracle.request(headers = listOf("User-Agent" to "Kobo/1.0"), remoteAddr = "192.168.1.10"))
  private val plainDetails = WebAuthenticationDetails(WebOracle.request(remoteAddr = "10.1.1.1"))

  private fun last(): List<Any?> = db.rawQuery("SELECT USER_ID, EMAIL, API_KEY_ID, API_KEY_COMMENT, IP, USER_AGENT, SUCCESS, ERROR, SOURCE FROM AUTHENTICATION_ACTIVITY ORDER BY rowid DESC LIMIT 1").firstOrNull() ?: emptyList<Any?>()

  private fun count() = db.rawQuery("SELECT count(*) FROM AUTHENTICATION_ACTIVITY").first().first()

  override fun cases() {
    func("onSuccess") {
      case("populate") { db.komgaUserDao.insert(user) }
      case("password") {
        listener.onSuccess(AuthenticationSuccessEvent(UsernamePasswordAuthenticationToken.authenticated(KomgaPrincipal(user), null, roles).apply { this.details = this@LoginListenerOracleTest.details }))
        last()
      }
      case("api key") {
        listener.onSuccess(AuthenticationSuccessEvent(ApiKeyAuthenticationToken.authenticated(KomgaPrincipal(user, apiKey = apiKey, name = "masked"), null, roles).apply { this.details = this@LoginListenerOracleTest.details }))
        last()
      }
      case("remember me, plain details") {
        listener.onSuccess(AuthenticationSuccessEvent(RememberMeAuthenticationToken("key", KomgaPrincipal(user), roles).apply { this.details = plainDetails }))
        last()
      }
      case("anonymous source, no details") {
        listener.onSuccess(AuthenticationSuccessEvent(AnonymousAuthenticationToken("key", KomgaPrincipal(user), roles)))
        last()
      }
    }
    func("onFailure") {
      case("bad credentials, known email") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("USER@example.org", "wrong").apply { this.details = this@LoginListenerOracleTest.details }, BadCredentialsException("Bad credentials")))
        last()
      }
      case("bad credentials, unknown email") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("nobody@example.org", "wrong"), BadCredentialsException("Bad credentials")))
        last()
      }
      case("api key") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(ApiKeyAuthenticationToken.unauthenticated("masked-key", "hashed").apply { this.details = this@LoginListenerOracleTest.details }, BadCredentialsException("Bad credentials")))
        last()
      }
      case("disabled, empty message") {
        listener.onFailure(AuthenticationFailureDisabledEvent(RememberMeAuthenticationToken("k", "user@example.org", roles), org.springframework.security.authentication.DisabledException("")))
        last()
      }
      case("null principal") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated(null, null), BadCredentialsException("x")))
        last()
      }
      case("provider not found is ignored") {
        val before = count()
        listener.onFailure(AuthenticationFailureProviderNotFoundEvent(UsernamePasswordAuthenticationToken.unauthenticated("a", "b"), BadCredentialsException("x")))
        listOf(before, count())
      }
    }
    func("getIp") {
      case("from user agent details") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("ip@example.org", "x").apply { this.details = this@LoginListenerOracleTest.details }, BadCredentialsException("x")))
        last()[4]
      }
      case("details of another type") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("ip@example.org", "x").apply { this.details = "string details" }, BadCredentialsException("x")))
        last()[4]
      }
    }
    func("getUserAgent") {
      case("from user agent details") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("ua@example.org", "x").apply { this.details = this@LoginListenerOracleTest.details }, BadCredentialsException("x")))
        last()[5]
      }
      case("plain web details") {
        listener.onFailure(AuthenticationFailureBadCredentialsEvent(UsernamePasswordAuthenticationToken.unauthenticated("ua@example.org", "x").apply { this.details = plainDetails }, BadCredentialsException("x")))
        listOf(last()[4], last()[5])
      }
    }
  }
}
