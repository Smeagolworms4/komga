package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.infrastructure.security.apikey.HeaderApiKeyAuthenticationConverter
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.infrastructure.security.apikey.ApiKeySupport.describeDetails

class HeaderApiKeyAuthenticationConverterOracleTest : OracleTest() {
  private val converter = HeaderApiKeyAuthenticationConverter("X-API-Key", ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)

  private fun convert(vararg headers: Pair<String, String>) =
    converter.convert(WebOracle.request(headers = headers.toList(), remoteAddr = "10.0.0.2"))?.let { WebOracle.describeAuthentication(it)!! + listOf(describeDetails(it.details)) }

  override fun cases() {
    func("convert") {
      case("no header") { convert() }
      case("other header") { convert("X-Auth-User" to "key") }
      case("key") { convert("X-API-Key" to "key-one") }
      case("header name case") { convert("x-api-key" to "key-one") }
      case("empty key") { convert("X-API-Key" to "") }
      case("blank key") { convert("X-API-Key" to "  ") }
      case("unicode key") { convert("X-API-Key" to "clé-漫画") }
      case("long key") { convert("X-API-Key" to "k".repeat(1000)) }
      case("user agent") { convert("X-API-Key" to "abc", "User-Agent" to "Kobo Touch/4.38") }
      case("two headers") { convert("X-API-Key" to "first", "X-API-Key" to "second") }
      case("other converter header") {
        HeaderApiKeyAuthenticationConverter("X-Auth-User", ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)
          .convert(WebOracle.request(headers = listOf("X-Auth-User" to "koreader")))
          ?.let { WebOracle.describeAuthentication(it) }
      }
    }
  }
}
