package org.gotson.komga.oracle.infrastructure.configuration

import org.gotson.komga.infrastructure.configuration.StaticConfiguration
import org.gotson.komga.oracle.OracleTest

class StaticConfigurationOracleTest : OracleTest() {
  override fun cases() {
    val c = StaticConfiguration()
    func("thumbnailType") {
      case("value") { c.thumbnailType() }
      case("media type") { c.thumbnailType().let { listOf(it.mediaType, it.imageIOFormat) } }
    }
    func("pdfImageType") {
      case("value") { c.pdfImageType() }
      case("same as thumbnail type") { c.pdfImageType() == c.thumbnailType() }
    }
    func("pdfResolution") {
      case("value") { c.pdfResolution() }
    }
  }
}
