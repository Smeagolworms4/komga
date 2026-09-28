package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.interfaces.api.rest.FontsController
import org.gotson.komga.oracle.OracleTest
import org.springframework.http.ResponseEntity
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class FontsControllerOracleTest : OracleTest() {
  /** Same tree in the TypeScript twin: one file per style/weight group, so the listing order does not matter */
  private val controller: FontsController by lazy {
    val dir = tempDir.resolve("fonts")
    mapOf(
      "Solo" to listOf("Solo-Regular.TTF"),
      "Duo" to listOf("duo-bolditalic.otf", "readme.txt", "Duo.woff.bak"),
      "Other" to listOf("x.woff2"),
      "Empty" to emptyList(),
    ).forEach { (family, files) ->
      dir.resolve(family).createDirectories()
      files.forEach { dir.resolve(family).resolve(it).writeBytes(oracleBytes(8)) }
    }
    dir.resolve("stray.ttf").writeBytes(oracleBytes(1))
    FontsController(KomgaProperties().apply { fonts.dataDirectory = dir.toString() })
  }

  private fun head(e: ResponseEntity<*>) = listOf(e.statusCode.value(), e.headers.getFirst("Content-Disposition"), e.headers.getFirst("Content-Type"))

  override fun cases() {
    func("getFonts") {
      case("sorted families") { controller.getFonts().sorted() }
      case("no data directory") { FontsController(KomgaProperties().apply { fonts.dataDirectory = tempDir.resolve("none").toString() }).getFonts().sorted() }
    }
    func("getFontFile") {
      case("additional font") { RestOracle.entity(controller.getFontFile("Solo", "Solo-Regular.TTF")) }
      case("otf") { RestOracle.entity(controller.getFontFile("Duo", "duo-bolditalic.otf")) }
      case("unsupported file") { controller.getFontFile("Duo", "readme.txt") }
      case("embedded font") { head(controller.getFontFile("OpenDyslexic", "OpenDyslexic-Bold.woff2")) }
      case("missing file") { controller.getFontFile("Solo", "nope.ttf") }
      case("missing family") { controller.getFontFile("Nope", "Solo-Regular.TTF") }
      case("family is case sensitive") { controller.getFontFile("solo", "Solo-Regular.TTF") }
    }
    func("getFontFamilyAsCss") {
      case("single file") { RestOracle.entity(controller.getFontFamilyAsCss("Solo")) }
      case("missing family") { controller.getFontFamilyAsCss("Nope") }
      case("empty family") { RestOracle.entity(controller.getFontFamilyAsCss("Empty")) }
    }
    func("buildFontFaceBlock") {
      case("truetype") { String(controller.getFontFamilyAsCss("Solo").body!!.contentAsByteArray) }
      case("opentype bold italic") { String(controller.getFontFamilyAsCss("Duo").body!!.contentAsByteArray) }
      case("woff2") { String(controller.getFontFamilyAsCss("Other").body!!.contentAsByteArray) }
      case("embedded, several groups") { String(controller.getFontFamilyAsCss("OpenDyslexic").body!!.contentAsByteArray) }
    }
    func("getFontCharacteristics") {
      case("embedded groups") {
        String(controller.getFontFamilyAsCss("OpenDyslexic").body!!.contentAsByteArray)
          .lines()
          .filter { it.contains("font-weight") || it.contains("font-style") }
      }
    }
  }
}
