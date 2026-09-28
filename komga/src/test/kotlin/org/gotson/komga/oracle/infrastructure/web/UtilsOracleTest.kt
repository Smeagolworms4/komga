package org.gotson.komga.oracle.infrastructure.web

import org.gotson.komga.infrastructure.web.filePathToUrl
import org.gotson.komga.infrastructure.web.getCurrentRequest
import org.gotson.komga.infrastructure.web.getMediaTypeOrDefault
import org.gotson.komga.infrastructure.web.setCachePrivate
import org.gotson.komga.infrastructure.web.toFilePath
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.http.ResponseEntity
import java.net.URL

class UtilsOracleTest : OracleTest() {
  override fun cases() {
    func("toFilePath") {
      case("simple") { URL("file:/tmp/a/c.cbz").toFilePath() }
      case("encoded space") { URL("file:/tmp/a%20b/c.cbz").toFilePath() }
      case("triple slash") { URL("file:///data/b.cbz").toFilePath() }
      case("encoded unicode") { URL("file:/data/%C3%BC%E6%BC%AB.cbz").toFilePath() }
      case("raw unicode") { URL("file:/data/ü漫.cbz").toFilePath() }
      case("trailing slash") { URL("file:/data/dir/").toFilePath() }
      case("dot segments") { URL("file:/a/./b/../c").toFilePath() }
      case("encoded percent and hash") { URL("file:/a/%25%23b").toFilePath() }
      case("root") { URL("file:/").toFilePath() }
      case("http scheme") { exceptionType { URL("http://host/a").toFilePath() } }
      case("query") { exceptionType { URL("file:/a?b=c").toFilePath() } }
    }
    func("filePathToUrl") {
      case("simple") { filePathToUrl("/oracle-missing/a/c.cbz") }
      case("space") { filePathToUrl("/oracle-missing/a b/c d.cbz") }
      case("unicode") { filePathToUrl("/oracle-missing/ü漫画.cbz") }
      case("special characters") { filePathToUrl("/oracle-missing/#h%a[b]{c};d=e&f+g,h'i!j@k$.cbz") }
      case("trailing slash on missing path") { filePathToUrl("/oracle-missing/dir/") }
      case("double slash") { filePathToUrl("/oracle-missing//a") }
      case("round trip") { filePathToUrl("/oracle-missing/ü a#b/c%d.cbz").toFilePath() }
      case("existing directory gets a trailing slash") { filePathToUrl(tempDir.toString()).toString().endsWith("/") }
    }
    func("setCachePrivate") {
      case("ok") { WebOracle.describeEntity(ResponseEntity.ok().setCachePrivate().build<Any>()) }
      case("with body") { WebOracle.describeEntity(ResponseEntity.status(201).setCachePrivate().body("x")) }
      case("twice") { WebOracle.describeEntity(ResponseEntity.ok().setCachePrivate().setCachePrivate().build<Any>()) }
    }
    func("getMediaTypeOrDefault") {
      listOf(
        null,
        "image/jpeg",
        "IMAGE/JPEG",
        "text/html;charset=utf-8",
        "text/html; charset=\"UTF-8\"",
        "application/*+xml",
        "*/*",
        "*",
        "*/json",
        "",
        "  ",
        "invalid",
        "a/b/c",
        "application/vnd.comicbook+zip",
        "application/epub+zip; q=0.5",
        "text/plain;a",
        "text/plain;a=",
        "x/y; a=b; c=d",
        " image/png ",
        "image/png;",
        "image/ png",
        "text/plain; charset=unknown-charset",
      ).forEach { case("$it") { getMediaTypeOrDefault(it).toString() } }
    }
    func("getCurrentRequest") {
      case("no request") { getCurrentRequest().requestURI }
      case("bound request") { WebOracle.withRequest(WebOracle.request(uri = "/api/v1/books")) { getCurrentRequest().requestURI } }
      case("after the request") { getCurrentRequest() }
    }
  }
}
