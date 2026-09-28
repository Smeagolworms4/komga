package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.OAuth2Controller
import org.gotson.komga.oracle.OracleTest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType

class OAuth2ControllerOracleTest : OracleTest() {
  private fun reg(
    id: String,
    name: String,
  ) = ClientRegistration
    .withRegistrationId(id)
    .clientName(name)
    .clientId("client-$id")
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
    .authorizationUri("https://auth/$id")
    .tokenUri("https://token/$id")
    .build()

  override fun cases() {
    func("getOAuth2Providers") {
      case("no repository") { OAuth2Controller(null).getOAuth2Providers() }
      case("one provider") { OAuth2Controller(InMemoryClientRegistrationRepository(reg("github", "GitHub"))).getOAuth2Providers() }
      case("several providers") {
        OAuth2Controller(InMemoryClientRegistrationRepository(reg("zeta", "Zeta"), reg("alpha", "Alpha Provider"), reg("google", "Google"))).getOAuth2Providers()
      }
    }
  }
}
