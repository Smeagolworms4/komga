package org.gotson.komga.oracle.infrastructure.security.oauth2

import org.gotson.komga.infrastructure.security.oauth2.GithubOAuth2UserService
import org.gotson.komga.oracle.OracleTest

class GithubOAuth2UserServiceOracleTest : OracleTest() {
  private val idp by lazy { FakeIdentityProvider() }
  private val service = GithubOAuth2UserService()

  private fun load(vararg scopes: String): Any? =
    try {
      idp.describe(service.loadUser(idp.request(idp.registration("github", "/user", "login", *scopes))))
    } catch (e: Exception) {
      idp.describeError(e)
    }.let { listOf(it, idp.drain()) }

  override fun cases() {
    func("loadUser") {
      case("email in profile") {
        idp.responses["/user"] = 200 to """{"login":"gh","id":1,"email":"gh@example.org"}"""
        load("user:email")
      }
      case("email from emails endpoint") {
        idp.responses["/user"] = 200 to """{"login":"gh","id":1,"email":null}"""
        idp.responses["/user/emails"] = 200 to """[{"email":"a@x.org","verified":true,"primary":false},{"email":"b@x.org","verified":false,"primary":true},{"email":"c@x.org","verified":true,"primary":true}]"""
        load("user:email", "read:user")
      }
      case("no verified primary email") {
        idp.responses["/user/emails"] = 200 to """[{"email":"a@x.org","verified":true,"primary":false}]"""
        load("user")
      }
      case("emails endpoint failure") {
        idp.responses["/user/emails"] = 500 to "{}"
        load("user:email")
      }
      case("no email scope") { load("read:user") }
      case("profile failure") {
        idp.responses["/user"] = 401 to """{"message":"Bad credentials"}"""
        load("user:email")
      }
      case("null request") {
        try {
          service.loadUser(null)
        } catch (e: Exception) {
          idp.describeError(e)
        }
      }
      case("stop") { idp.stop() }
    }
  }
}
