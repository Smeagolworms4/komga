package org.gotson.komga.oracle.infrastructure.security.oauth2

import com.sun.net.httpserver.HttpServer
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.OAuth2User
import java.net.InetSocketAddress
import java.time.Instant

/**
 * Local identity provider for the OAuth2 oracles, mirrored by test/unit/infrastructure/security/oauth2/support.ts:
 * answers each path with a configured status and JSON body, and records the requests (method, path, Authorization header).
 */
class FakeIdentityProvider {
  val requests = mutableListOf<List<Any?>>()
  val responses = mutableMapOf<String, Pair<Int, String>>()
  private val server =
    HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
      createContext("/") { exchange ->
        requests.add(listOf(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization")))
        val (status, body) = responses[exchange.requestURI.path] ?: (404 to "{}")
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      start()
    }

  val base = "http://127.0.0.1:${server.address.port}"

  fun stop() = server.stop(0)

  fun drain(): List<List<Any?>> = requests.toList().also { requests.clear() }

  fun clean(s: String?) = s?.replace(base, "<idp>")

  fun registration(
    id: String,
    userInfoPath: String?,
    userNameAttribute: String,
    vararg scopes: String,
  ): ClientRegistration =
    ClientRegistration
      .withRegistrationId(id)
      .clientId("client")
      .clientSecret("secret")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
      .authorizationUri("$base/authorize")
      .tokenUri("$base/token")
      .userInfoUri(userInfoPath?.let { "$base$it" })
      .userNameAttributeName(userNameAttribute)
      .scope(*scopes)
      .clientName(id)
      .build()

  fun accessToken(vararg scopes: String) = OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"), scopes.toSet())

  fun request(registration: ClientRegistration) = OAuth2UserRequest(registration, accessToken(*(registration.scopes ?: emptySet()).toTypedArray()))

  fun describe(u: OAuth2User): List<Any?> =
    listOf(
      u.name,
      u.attributes.toSortedMap().map { listOf(it.key, it.value?.toString()) },
      u.authorities.map { it.authority }.sorted(),
      (u as? KomgaPrincipal)?.let { listOf(it.user.email, it.user.id.length) },
    )

  fun describeError(e: Throwable): List<Any?> = listOf(e::class.java.simpleName, (e as? OAuth2AuthenticationException)?.error?.errorCode, clean(e.message))
}
