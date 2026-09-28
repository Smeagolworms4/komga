package org.gotson.komga.oracle.infrastructure.security

import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.UserRoles
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.oracle.OracleTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.time.Instant
import java.time.LocalDateTime

class KomgaPrincipalOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 5, 6, 7, 8, 9)
  private val user = KomgaUser("user@example.org", "{bcrypt}hash", roles = setOf(UserRoles.PAGE_STREAMING, UserRoles.FILE_DOWNLOAD), id = "U1", createdDate = date)
  private val admin = KomgaUser("admin@example.org", "pw", roles = UserRoles.entries.toSet(), id = "U2", createdDate = date)
  private val noRoles = KomgaUser("none@example.org", "", roles = emptySet(), id = "U3", createdDate = date)
  private val oauth2 = DefaultOAuth2User(listOf(SimpleGrantedAuthority("OAUTH2_USER")), mapOf("login" to "gh-user", "email" to "gh@example.org"), "login")
  private val idToken = OidcIdToken("token", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-01T01:00:00Z"), mapOf("sub" to "sub-1", "email" to "oidc@example.org"))
  private val oidc = DefaultOidcUser(listOf(SimpleGrantedAuthority("OIDC_USER")), idToken, OidcUserInfo(mapOf("sub" to "sub-1", "name" to "Oidc Name")))
  private val apiKey = ApiKey(id = "K1", userId = "U1", key = "hashed", comment = "c", createdDate = date)

  private fun sorted(m: Map<String, Any>) = m.toSortedMap().map { listOf(it.key, it.value.toString()) }

  private val principals =
    listOf(
      "user" to KomgaPrincipal(user),
      "admin" to KomgaPrincipal(admin),
      "no roles" to KomgaPrincipal(noRoles),
      "oauth2" to KomgaPrincipal(user, oAuth2User = oauth2),
      "oidc" to KomgaPrincipal(user, oidcUser = oidc),
      "api key with name" to KomgaPrincipal(user, apiKey = apiKey, name = "masked-name"),
      "empty name" to KomgaPrincipal(user, name = ""),
    )

  override fun cases() {
    func("getAuthorities") { principals.forEach { (n, p) -> case(n) { p.authorities.map { it.authority }.sorted() } } }
    func("isEnabled") { principals.forEach { (n, p) -> case(n) { p.isEnabled } } }
    func("getUsername") { principals.forEach { (n, p) -> case(n) { p.username } } }
    func("isCredentialsNonExpired") { principals.forEach { (n, p) -> case(n) { p.isCredentialsNonExpired } } }
    func("getPassword") { principals.forEach { (n, p) -> case(n) { p.password } } }
    func("isAccountNonExpired") { principals.forEach { (n, p) -> case(n) { p.isAccountNonExpired } } }
    func("isAccountNonLocked") { principals.forEach { (n, p) -> case(n) { p.isAccountNonLocked } } }
    func("getName") { principals.forEach { (n, p) -> case(n) { p.name } } }
    func("getAttributes") { principals.forEach { (n, p) -> case(n) { sorted(p.attributes) } } }
    func("getClaims") { principals.forEach { (n, p) -> case(n) { sorted(p.claims) } } }
    func("getUserInfo") { principals.forEach { (n, p) -> case(n) { p.userInfo?.let { sorted(it.claims) } } } }
    func("getIdToken") { principals.forEach { (n, p) -> case(n) { p.idToken?.let { listOf(it.tokenValue, it.subject) } } } }
  }
}
