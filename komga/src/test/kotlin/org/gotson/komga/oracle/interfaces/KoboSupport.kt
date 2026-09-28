package org.gotson.komga.oracle.interfaces

import org.gotson.komga.infrastructure.kobo.KoboProxy
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory

/**
 * Fake Kobo store behind [KoboProxy], mirrored by test/unit/interfaces/kobo-support.ts: the RestClient of the proxy
 * is replaced by one whose requests are recorded (method, URL, headers with lower-cased names, body) and answered
 * with [status], [headers] and [body].
 */
class FakeKoboStore {
  val requests = mutableListOf<List<Any?>>()
  var status = 200
  var headers: List<Pair<String, String>> = emptyList()
  var body: String = "{}"

  private val factory =
    ClientHttpRequestFactory { uri, method ->
      object : MockClientHttpRequest(method, uri) {
        override fun executeInternal(): ClientHttpResponse {
          requests.add(
            listOf(
              method.name(),
              uri.toString(),
              getHeaders().map { (k, v) -> listOf(k.lowercase(), v) }.sortedBy { it[0] as String },
              bodyAsString,
            ),
          )
          return MockClientHttpResponse(this@FakeKoboStore.body.toByteArray(), HttpStatus.valueOf(this@FakeKoboStore.status)).apply {
            this@FakeKoboStore.headers.forEach { (k, v) -> getHeaders().add(k, v) }
          }
        }
      }
    }

  fun install(proxy: KoboProxy) {
    val client =
      RestClient
        .builder()
        .uriBuilderFactory(DefaultUriBuilderFactory("https://storeapi.kobo.com").apply { encodingMode = DefaultUriBuilderFactory.EncodingMode.NONE })
        .requestFactory(factory)
        .build()
    KoboProxy::class.java.getDeclaredField("koboApiClient").apply { isAccessible = true }.set(proxy, client)
  }

  fun respond(
    status: Int = 200,
    body: String = "{}",
    vararg headers: Pair<String, String>,
  ) {
    this.status = status
    this.body = body
    // the Kobo store answers JSON
    this.headers = headers.toList().let { h -> if (h.none { it.first.equals("Content-Type", ignoreCase = true) }) h + ("Content-Type" to "application/json") else h }
  }

  fun drain(): List<List<Any?>> = requests.toList().also { requests.clear() }
}
