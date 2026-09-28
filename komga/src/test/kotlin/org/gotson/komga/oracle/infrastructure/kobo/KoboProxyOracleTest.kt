package org.gotson.komga.oracle.infrastructure.kobo

import org.gotson.komga.domain.model.KomgaSyncToken
import org.gotson.komga.infrastructure.kobo.KoboProxy
import org.gotson.komga.infrastructure.kobo.KomgaSyncTokenGenerator
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.FakeKoboStore
import org.gotson.komga.oracle.interfaces.InterfacesServices
import org.springframework.http.ResponseEntity

class KoboProxyOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)
  private val tokens = KomgaSyncTokenGenerator(WebOracle.mapper)
  private val proxy by lazy { KoboProxy(WebOracle.mapper, tokens, services.settings).also { store.install(it) } }
  private val store = FakeKoboStore()

  private fun describe(e: ResponseEntity<*>): List<Any?> = WebOracle.describeEntity(e).let { listOf(it[0], it[1], WebOracle.mapper.writeValueAsString(it[2])) }

  private fun proxied(
    uri: String,
    method: String = "GET",
    query: String? = null,
    headers: List<Pair<String, String>> = emptyList(),
    body: ByteArray? = null,
    includeSyncToken: Boolean = false,
  ): Any? =
    try {
      WebOracle.withRequest(WebOracle.request(method = method, uri = uri, query = query, headers = headers)) {
        describe(proxy.proxyCurrentRequest(body, includeSyncToken))
      }
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, e.message)
    }.let { listOf(it, store.drain()) }

  private fun call(
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = KoboProxy::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
    m.isAccessible = true
    return m.invoke(proxy, *args)
  }

  override fun cases() {
    func("isEnabled") {
      case("default") { proxy.isEnabled() }
      case("enabled") {
        services.settings.koboProxy = true
        proxy.isEnabled()
      }
    }
    func("isKoboHeader") {
      listOf("X-Kobo-SyncToken", "x-kobo-", "X-KOBO-DEVICEID", "X-Kobo", "Authorization", "x-kobox", " x-kobo-a").forEach { case(it) { call("isKoboHeader", it) } }
    }
    func("proxyCurrentRequest") {
      case("no current request") {
        try {
          proxy.proxyCurrentRequest()
        } catch (e: Exception) {
          listOf(e::class.java.simpleName, e.message)
        }
      }
      case("not a kobo path") { proxied("/api/v1/books") }
      case("get with headers") {
        store.respond(200, """{"Resources":{"a":1}}""", "X-Kobo-Apitoken" to "e30=", "Content-Type" to "application/json", "x-kobo-recent-reads" to "1", "Set-Cookie" to "a=b")
        proxied(
          "/kobo/TOKEN-1/v1/initialization",
          query = "a=1&b=%20c",
          headers =
            listOf(
              "Authorization" to "Bearer abc",
              "User-Agent" to "Kobo Touch",
              "Accept" to "*/*",
              "Accept-Language" to "fr",
              "X-Kobo-DeviceId" to "dev",
              "X-Kobo-SyncToken" to "should.not.pass",
              "Cookie" to "a=b",
              "X-Forwarded-For" to "1.2.3.4",
            ),
        )
      }
      case("post with body") {
        store.respond(201, """{"ok":true}""")
        proxied("/kobo/t/v1/auth/device", "POST", headers = listOf("Content-Type" to "application/json"), body = """{"UserKey":"k"}""".toByteArray())
      }
      case("empty path") {
        store.respond(200, "[]")
        proxied("/kobo/t")
      }
      case("encoded path") {
        store.respond(200, "{}")
        proxied("/kobo/t/v1/products/a%20b/%C3%A9")
      }
      case("sync token forwarded and updated") {
        store.respond(200, "[]", "x-kobo-synctoken" to "new-raw", "X-Kobo-Sync" to "continue")
        proxied(
          "/kobo/t/v1/library/sync",
          headers = listOf("X-Kobo-SyncToken" to tokens.toBase64(KomgaSyncToken(rawKoboSyncToken = "old-raw", ongoingSyncPointId = "SP1"))),
          includeSyncToken = true,
        )
      }
      case("sync token with blank raw token") {
        store.respond(200, "[]", "X-Kobo-SyncToken" to "new-raw")
        proxied("/kobo/t/v1/library/sync", headers = listOf("X-Kobo-SyncToken" to tokens.toBase64(KomgaSyncToken())), includeSyncToken = true)
      }
      case("sync token not requested") {
        store.respond(200, "[]", "X-Kobo-SyncToken" to "new-raw")
        proxied("/kobo/t/v1/library/sync", headers = listOf("X-Kobo-SyncToken" to "a.b"))
      }
      case("no sync token in request") {
        store.respond(200, "[]", "X-Kobo-SyncToken" to "new-raw")
        proxied("/kobo/t/v1/library/sync", includeSyncToken = true)
      }
      case("error status") {
        store.respond(401, """{"error":"x"}""")
        proxied("/kobo/t/v1/user/profile")
      }
      case("server error") {
        store.respond(503, "")
        proxied("/kobo/t/v1/user/profile", "PUT", body = ByteArray(0))
      }
      case("empty body") {
        store.respond(200, "")
        proxied("/kobo/t/v1/analytics/event", "POST", body = "x".toByteArray())
      }
      case("disabled") {
        services.settings.koboProxy = false
        store.respond(200, "{}")
        proxied("/kobo/t/v1/initialization")
      }
    }
  }
}
