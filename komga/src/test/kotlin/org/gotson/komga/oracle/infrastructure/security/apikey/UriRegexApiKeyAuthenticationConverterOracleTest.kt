package org.gotson.komga.oracle.infrastructure.security.apikey

import org.gotson.komga.infrastructure.security.apikey.UriRegexApiKeyAuthenticationConverter
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.infrastructure.security.apikey.ApiKeySupport.describeDetails

class UriRegexApiKeyAuthenticationConverterOracleTest : OracleTest() {
  private val converter = UriRegexApiKeyAuthenticationConverter(Regex("""/kobo/([\w-]+)"""), ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)

  private fun convert(
    uri: String,
    c: UriRegexApiKeyAuthenticationConverter = converter,
  ) = c.convert(WebOracle.request(uri = uri, headers = listOf("User-Agent" to "Kobo"), remoteAddr = "0:0:0:0:0:0:0:1"))?.let { WebOracle.describeAuthentication(it)!! + listOf(describeDetails(it.details)) }

  override fun cases() {
    func("convert") {
      listOf(
        "/kobo/key-one/v1/library/sync",
        "/kobo/key-one",
        "/kobo/",
        "/kobo",
        "/api/v1/books",
        "/kobo/abc_DEF-123/v1",
        "/kobo/a.b/v1",
        "/kobo/%20x/v1",
        "/kobo/%C3%A9t%C3%A9/v1",
        "/prefix/kobo/key/v1",
        "/kobo/k1/kobo/k2",
        "/KOBO/key/v1",
        "/kobo/-/v1",
      ).forEach { uri -> case(uri) { convert(uri) } }
      case("regex with two groups: last group") { convert("/kobo/abc/def", UriRegexApiKeyAuthenticationConverter(Regex("/kobo/(\\w+)/(\\w+)"), ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)) }
      case("regex without group: whole match") { convert("/kobo/abc/def", UriRegexApiKeyAuthenticationConverter(Regex("/kobo/\\w+"), ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)) }
      case("optional group not matched") { convert("/kobo/", UriRegexApiKeyAuthenticationConverter(Regex("/kobo/(\\w+)?"), ApiKeySupport.hasher, ApiKeySupport.tokenEncoder, ApiKeySupport.detailsSource)) }
    }
  }
}
