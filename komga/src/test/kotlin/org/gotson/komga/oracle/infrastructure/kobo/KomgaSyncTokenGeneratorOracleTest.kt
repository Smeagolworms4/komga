package org.gotson.komga.oracle.infrastructure.kobo

import org.gotson.komga.domain.model.KomgaSyncToken
import org.gotson.komga.infrastructure.kobo.KomgaSyncTokenGenerator
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import java.util.Base64

class KomgaSyncTokenGeneratorOracleTest : OracleTest() {
  private val generator = KomgaSyncTokenGenerator(WebOracle.mapper)

  private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

  private val full = KomgaSyncToken(version = 3, rawKoboSyncToken = "abc.def", ongoingSyncPointId = "SP2", lastSuccessfulSyncPointId = "SP1")

  override fun cases() {
    func("fromBase64") {
      case("komga token round trip") { generator.fromBase64(generator.toBase64(full)) }
      case("komga default token round trip") { generator.fromBase64(generator.toBase64(KomgaSyncToken())) }
      case("komga token unicode") { generator.fromBase64(generator.toBase64(KomgaSyncToken(rawKoboSyncToken = "ünï 漫画 \"quoted\" \\ /"))) }
      case("komga padded base64") { generator.fromBase64("KOMGA." + b64("""{"version":2}""")) }
      case("komga partial json") { generator.fromBase64("KOMGA." + b64("""{"ongoingSyncPointId":"X"}""")) }
      case("komga unknown property") { generator.fromBase64("KOMGA." + b64("""{"unknown":1,"version":4}""")) }
      case("komga case insensitive property") { generator.fromBase64("KOMGA." + b64("""{"VERSION":5,"rawkobosynctoken":"r"}""")) }
      case("komga null for non-null property") { generator.fromBase64("KOMGA." + b64("""{"rawKoboSyncToken":null}""")) }
      case("komga null for primitive") { generator.fromBase64("KOMGA." + b64("""{"version":null}""")) }
      case("komga version as string") { generator.fromBase64("KOMGA." + b64("""{"version":"7"}""")) }
      case("komga invalid base64") { generator.fromBase64("KOMGA.!!!") }
      case("komga url-safe base64") { generator.fromBase64("KOMGA.-_-_") }
      case("komga dotted payload") { generator.fromBase64("KOMGA.abc.def") }
      case("komga not json") { generator.fromBase64("KOMGA." + b64("hello")) }
      case("komga empty") { generator.fromBase64("KOMGA.") }
      case("komga json array") { generator.fromBase64("KOMGA." + b64("[1]")) }
      case("komga trailing content") { generator.fromBase64("KOMGA." + b64("""{"version":2} x""")) }
      case("calibre web token") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":"store.token"}}""")) }
      case("calibre web token number") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":12}}""")) }
      case("calibre web token decimal") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":1.50}}""")) }
      case("calibre web token boolean") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":true}}""")) }
      case("calibre web token null") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":null}}""")) }
      case("calibre web token object") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":{"a":1}}}""")) }
      case("calibre web token array") { generator.fromBase64(b64("""{"data":{"raw_kobo_store_token":[1]}}""")) }
      case("calibre web missing data") { generator.fromBase64(b64("""{"other":1}""")) }
      case("calibre web missing token") { generator.fromBase64(b64("""{"data":{}}""")) }
      case("calibre web data not object") { generator.fromBase64(b64("""{"data":"x"}""")) }
      case("calibre web json array") { generator.fromBase64(b64("[1,2]")) }
      case("calibre web not json") { generator.fromBase64(b64("not json")) }
      case("calibre web invalid base64") { generator.fromBase64("%%%") }
      case("empty string") { generator.fromBase64("") }
      case("kobo store token") { generator.fromBase64("eyJhIjoxfQ.eyJiIjoyfQ") }
      case("kobo store token single dot") { generator.fromBase64(".") }
      case("kobo store token any dotted string") { generator.fromBase64("not base64 . at all") }
      case("prefix only lower case") { generator.fromBase64("komga." + b64("""{"version":2}""")) }
    }
    func("toBase64") {
      case("default token") { generator.toBase64(KomgaSyncToken()) }
      case("full token") { generator.toBase64(full) }
      case("unicode token") { generator.toBase64(KomgaSyncToken(rawKoboSyncToken = "ünï 漫画 \"q\" \\ / \n \u0001")) }
      case("padding removed") { (0..5).map { generator.toBase64(KomgaSyncToken(rawKoboSyncToken = "x".repeat(it))) } }
    }
    func("fromRequestHeaders") {
      case("no header") { generator.fromRequestHeaders(WebOracle.request()) }
      case("komga token") { generator.fromRequestHeaders(WebOracle.request(headers = listOf("X-Kobo-SyncToken" to generator.toBase64(full)))) }
      case("header name case insensitive") { generator.fromRequestHeaders(WebOracle.request(headers = listOf("x-kobo-synctoken" to "a.b"))) }
      case("empty header") { generator.fromRequestHeaders(WebOracle.request(headers = listOf("X-Kobo-SyncToken" to ""))) }
      case("two headers, first wins") { generator.fromRequestHeaders(WebOracle.request(headers = listOf("X-Kobo-SyncToken" to "first.one", "X-Kobo-SyncToken" to "second.one"))) }
    }
  }
}
